package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceEntity;
import com.example.processengine.runtime.StepInstanceStatus;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PauseResumeTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void pauseBlocksUserTaskCompletionAndResumeAllowsItAgain() {
        engine.deploy("order-pause", DemoProcessDefinitions.v1());
        var instance = engine.start("order-pause", Map.of("orderId", "ORD-P1"));
        var instanceId = instance.getId();

        var reviewTask = awaitActive(instanceId, "reviewOrder");

        engine.pause(instanceId);
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.PAUSED);
        assertThatThrownBy(() -> engine.completeStep(instanceId, reviewTask.getId(), Map.of()))
                .isInstanceOf(IllegalStateException.class);

        engine.resume(instanceId);
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.RUNNING);

        engine.completeStep(instanceId, reviewTask.getId(), Map.of());
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
    }

    @Test
    void pausingWhileAServiceTaskIsInFlightDefersAdvanceUntilResume() {
        // maxAttempts=1 so the single execution either succeeds (advance deferred by pause) or fails outright.
        engine.deploy("flaky-pause", EngineTestSupport.flakySingleStepSpec(1));
        var instance = engine.start("flaky-pause", Map.of("flakyFailUntilCount", 0));
        var instanceId = instance.getId();

        // Race pause() against the in-flight handler; either the handler wins (COMPLETED before pause
        // even applies) or pause wins and we verify resume() is what finally advances it to COMPLETED.
        engine.pause(instanceId);

        var afterPause = instanceRepo.findById(instanceId).orElseThrow().getStatus();
        assertThat(afterPause).isIn(InstanceStatus.PAUSED, InstanceStatus.COMPLETED);

        if (afterPause == InstanceStatus.PAUSED) {
            // give the in-flight handler time to finish; instance must stay PAUSED, not silently complete
            sleepMillis(300);
            assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus())
                    .isEqualTo(InstanceStatus.PAUSED);
            engine.resume(instanceId);
        }

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
    }

    private StepInstanceEntity awaitActive(UUID instanceId, String stepKey) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var found = engine.stepsOf(instanceId).stream()
                    .anyMatch(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE);
            assertThat(found).isTrue();
        });
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
