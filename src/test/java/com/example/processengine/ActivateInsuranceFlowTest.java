package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.ActivateInsuranceProcessDefinitions;
import com.example.processengine.demo.ActivateInsuranceRollbackProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
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
 * End-to-end coverage of the bundled activate-insurance process: the steps that create something outside the engine
 * are compensable, so a failure reverses what was done, and a guarded step then follows its failure route to the
 * rollback process, which only logs.
 */
@SpringBootTest
@ActiveProfiles("test")
class ActivateInsuranceFlowTest {

    @Autowired
    private ProcessEngine engine;

    private void deploy() {
        engine.deploy(
                ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY,
                ActivateInsuranceRollbackProcessDefinitions.v1());
        engine.deploy(ActivateInsuranceProcessDefinitions.PROCESS_KEY, ActivateInsuranceProcessDefinitions.v1());
    }

    private void awaitStatus(UUID instanceId, InstanceStatus expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> assertThat(engine.get(instanceId).getStatus()).isEqualTo(expected));
    }

    private StepInstanceStatus statusOf(UUID instanceId, String stepKey) {
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    @Test
    void anUnimpededRunCompletesWithEveryStepDone() {
        deploy();

        var instance = engine.start(ActivateInsuranceProcessDefinitions.PROCESS_KEY, Map.of("fullySigned", true));

        awaitStatus(instance.getId(), InstanceStatus.COMPLETED);
        assertThat(statusOf(instance.getId(), "createAsset")).isEqualTo(StepInstanceStatus.COMPLETED);
    }

    @Test
    void aMatchingBusinessErrorCompensatesTheCompletedStepsAndThenFollowsTheRouteToTheRollbackProcess() {
        deploy();

        var instance = engine.start(
                ActivateInsuranceProcessDefinitions.PROCESS_KEY,
                Map.of(
                        "fullySigned", true,
                        "createAssetFail", true,
                        "createAssetFailErrorCode", "ROLLBACK_ERROR"));

        awaitStatus(instance.getId(), InstanceStatus.COMPLETED);
        assertThat(statusOf(instance.getId(), "activateInsuranceAgi")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(instance.getId(), "createCollateral")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(instance.getId(), "createAsset")).isEqualTo(StepInstanceStatus.FAILED);
        assertThat(statusOf(instance.getId(), "rollback")).isEqualTo(StepInstanceStatus.COMPLETED);
    }

    @Test
    void aTechnicalFailureOfAGuardedStepIsNotRoutedButStillCompensatesTheCompletedSteps() {
        deploy();

        var instance = engine.start(
                ActivateInsuranceProcessDefinitions.PROCESS_KEY, Map.of("fullySigned", true, "createAssetFail", true));

        awaitStatus(instance.getId(), InstanceStatus.FAILED);
        assertThat(statusOf(instance.getId(), "createCollateral")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(engine.stepsOf(instance.getId()))
                .noneMatch(s -> s.getStepKey().equals("rollback"));
    }

    @Test
    void theUnguardedStepFailingFailsTheInstanceWithNothingYetToCompensate() {
        deploy();

        var instance = engine.start(
                ActivateInsuranceProcessDefinitions.PROCESS_KEY,
                Map.of("fullySigned", true, "activateInsuranceAgiFail", true));

        awaitStatus(instance.getId(), InstanceStatus.FAILED);
        assertThat(statusOf(instance.getId(), "activateInsuranceAgi")).isEqualTo(StepInstanceStatus.FAILED);
    }
}
