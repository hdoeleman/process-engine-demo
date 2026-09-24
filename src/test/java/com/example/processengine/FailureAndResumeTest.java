package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceRepository;
import com.example.processengine.runtime.StepInstanceStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FailureAndResumeTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Autowired
    private StepInstanceRepository stepInstanceRepo;

    @Test
    void instanceFailsAfterExhaustingRetriesThenSucceedsOnManualResume() {
        engine.deploy("flaky-resume", EngineTestSupport.flakySingleStepSpec(1)); // maxAttempts=1, no auto-retry
        var instance = engine.start("flaky-resume", Map.of("flakyFailUntilCount", 1));
        var instanceId = instance.getId();

        // first (only) attempt fails -> instance FAILED
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);
        var failedStep =
                stepInstanceRepo.findByProcessInstance_IdAndStatus(instanceId, StepInstanceStatus.FAILED).stream()
                        .findFirst()
                        .orElseThrow();
        assertThat(failedStep.getStepKey()).isEqualTo("flakyStep");
        assertThat(failedStep.getAttempt()).isEqualTo(1);

        // operator resumes -- engine re-triggers the step; the (externally simulated) problem is
        // now resolved, so this second-ever call to the handler succeeds
        engine.resume(instanceId);

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
        var finished = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(finished.getVariables()).containsEntry("flakyDone", true);
    }

    @Test
    void killStopsAnInstancePermanently() {
        engine.deploy("flaky-kill", EngineTestSupport.flakySingleStepSpec(1));
        var instance = engine.start("flaky-kill", Map.of("flakyFailUntilCount", 1));
        var instanceId = instance.getId();

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);
        engine.kill(instanceId);

        var killed = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(killed.getStatus()).isEqualTo(InstanceStatus.KILLED);
        assertThatThrownBy(() -> engine.resume(instanceId)).isInstanceOf(IllegalStateException.class);
    }
}
