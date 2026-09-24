package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

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
class MoveToStepTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void movingBackwardToReviewOrderCancelsInFlightWorkAndReplaysFromThere() {
        engine.deploy("order-jump", DemoProcessDefinitions.v1());
        var instance = engine.start("order-jump", Map.of("orderId", "ORD-J1"));
        var instanceId = instance.getId();

        var firstReview = awaitActive(instanceId, "reviewOrder");
        engine.completeStep(instanceId, firstReview.getId(), Map.of());

        // let the parallel branches + join + gateway + invoice run to completion first
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getVariables())
                .containsKey("invoiceId");

        // now jump backward to reviewOrder as if the operator wants to redo the review
        engine.moveToStep(instanceId, "reviewOrder");
        var afterJump = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(afterJump.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        var secondReview = awaitActive(instanceId, "reviewOrder");
        assertThat(secondReview.getId()).isNotEqualTo(firstReview.getId());

        engine.completeStep(instanceId, secondReview.getId(), Map.of("simulateDeclined", true));

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getVariables())
                .containsEntry("cancelled", true);
    }

    private StepInstanceEntity awaitActive(UUID instanceId, String stepKey) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var found = engine.stepsOf(instanceId).stream()
                    .anyMatch(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE);
            assertThat(found).isTrue();
        });
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE)
                .reduce((first, second) -> second) // latest activation of this step
                .orElseThrow();
    }
}
