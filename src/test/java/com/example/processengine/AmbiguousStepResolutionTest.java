package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.processengine.demo.handlers.HangingHandler;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.engine.ProcessRecoveryService;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceEntity;
import com.example.processengine.runtime.StepInstanceRepository;
import com.example.processengine.runtime.StepInstanceStatus;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the ADR-011 dispatch guard: a SERVICE_TASK whose dispatch was recorded but whose
 * outcome never came back (simulated here with a handler blocked on a latch, standing in for a
 * crash mid-call) must be flagged AMBIGUOUS on recovery rather than blindly re-invoked, and must
 * only move forward again once an operator calls {@code resolveAmbiguousStep}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AmbiguousStepResolutionTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessRecoveryService recoveryService;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Autowired
    private StepInstanceRepository stepInstanceRepo;

    @Test
    void staleDispatchWithNoOutcomeIsFlaggedAmbiguousOnRecoveryNotBlindlyRetried() {
        var latch = HangingHandler.arm();
        try {
            engine.deploy("hang-ambiguous", EngineTestSupport.hangingSingleStepSpec(1));
            var instance = engine.start("hang-ambiguous", Map.of());
            var instanceId = instance.getId();

            var dispatched = awaitDispatched(instanceId, "hangStep");
            assertThat(dispatched.getDispatchedAttempt()).isEqualTo(1);
            assertThat(dispatched.getAttempt()).isEqualTo(0);

            recoveryService.recoverInstance(instanceId);

            var ambiguous =
                    stepInstanceRepo.findById(dispatched.getId()).orElseThrow();
            assertThat(ambiguous.getStatus()).isEqualTo(StepInstanceStatus.AMBIGUOUS);
            assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus())
                    .isEqualTo(InstanceStatus.RUNNING);
        } finally {
            latch.countDown();
        }
    }

    @Test
    void resolvingAmbiguousStepAsSucceededAdvancesTheInstance() {
        var latch = HangingHandler.arm();
        try {
            engine.deploy("hang-resolve-success", EngineTestSupport.hangingSingleStepSpec(1));
            var instance = engine.start("hang-resolve-success", Map.of());
            var instanceId = instance.getId();

            var dispatched = awaitDispatched(instanceId, "hangStep");
            recoveryService.recoverInstance(instanceId);

            engine.resolveAmbiguousStep(instanceId, dispatched.getId(), true, Map.of("resolvedByOperator", true));

            EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
            assertThat(instanceRepo.findById(instanceId).orElseThrow().getVariables())
                    .containsEntry("resolvedByOperator", true);
        } finally {
            latch.countDown();
        }
    }

    @Test
    void resolvingAmbiguousStepAsFailedExhaustsRetriesAndFailsTheInstance() {
        var latch = HangingHandler.arm();
        try {
            // maxAttempts=1: the operator's failure resolution is itself the one-and-only attempt.
            engine.deploy("hang-resolve-fail", EngineTestSupport.hangingSingleStepSpec(1));
            var instance = engine.start("hang-resolve-fail", Map.of());
            var instanceId = instance.getId();

            var dispatched = awaitDispatched(instanceId, "hangStep");
            recoveryService.recoverInstance(instanceId);

            engine.resolveAmbiguousStep(instanceId, dispatched.getId(), false, null);

            EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);
            var failed =
                    stepInstanceRepo.findById(dispatched.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(StepInstanceStatus.FAILED);
            assertThat(failed.getAttempt()).isEqualTo(1);
        } finally {
            latch.countDown();
        }
    }

    @Test
    void resolvingAmbiguousStepAsFailedWithAttemptsRemainingRetriesAndSucceeds() {
        var latch = HangingHandler.arm();
        try {
            engine.deploy("hang-resolve-retry", EngineTestSupport.hangingSingleStepSpec(2));
            var instance = engine.start("hang-resolve-retry", Map.of());
            var instanceId = instance.getId();

            var dispatched = awaitDispatched(instanceId, "hangStep");
            recoveryService.recoverInstance(instanceId);

            // Releasing before resolving lets the retry this triggers run for real (the latch
            // stays at 0 forever, so every subsequent handler call returns immediately) --
            // otherwise the second dispatch would hang on the same latch as the first.
            latch.countDown();
            engine.resolveAmbiguousStep(instanceId, dispatched.getId(), false, null);

            EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
        } finally {
            latch.countDown();
        }
    }

    @Test
    void resolvingAStepThatIsNotAmbiguousIsRejected() {
        engine.deploy("hang-not-ambiguous", EngineTestSupport.flakySingleStepSpec(1));
        var instance = engine.start("hang-not-ambiguous", Map.of("flakyFailUntilCount", 0));
        var instanceId = instance.getId();

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);
        var completed =
                stepInstanceRepo.findByProcessInstance_IdAndStatus(instanceId, StepInstanceStatus.COMPLETED).stream()
                        .findFirst()
                        .orElseThrow();

        assertThatThrownBy(() -> engine.resolveAmbiguousStep(instanceId, completed.getId(), true, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private StepInstanceEntity awaitDispatched(UUID instanceId, String stepKey) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(20))
                .untilAsserted(() -> {
                    var si =
                            stepInstanceRepo
                                    .findByProcessInstance_IdAndStatus(instanceId, StepInstanceStatus.ACTIVE)
                                    .stream()
                                    .filter(s -> s.getStepKey().equals(stepKey))
                                    .findFirst()
                                    .orElseThrow();
                    assertThat(si.getDispatchedAttempt()).isGreaterThan(0);
                });
        return stepInstanceRepo.findByProcessInstance_IdAndStatus(instanceId, StepInstanceStatus.ACTIVE).stream()
                .filter(s -> s.getStepKey().equals(stepKey))
                .findFirst()
                .orElseThrow();
    }
}
