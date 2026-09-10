package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.EventType;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceEntity;
import com.example.processengine.runtime.StepInstanceStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the saga-style compensation infra: a permanent failure anywhere in the instance must
 * unwind every already-COMPLETED step, most-recently-completed first -- calling compensate() on
 * steps whose handler opted in ({@link com.example.processengine.engine.CompensatingStepHandler}),
 * and just cancelling the rest (they declared nothing external worth undoing).
 */
@SpringBootTest
@ActiveProfiles("test")
class SagaCompensationTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void permanentServiceTaskFailureCompensatesAlreadyCompletedSaga() {
        engine.deploy("order-saga-fail", DemoProcessDefinitions.v1());
        // chargePaymentFailAttempts=99 with maxAttempts=3 (see DemoProcessDefinitions.v1) means every
        // attempt throws, so the step exhausts its retries and fails permanently.
        var instance =
                engine.start("order-saga-fail", Map.of("orderId", "ORD-SAGA-1", "chargePaymentFailAttempts", 99));
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);

        // the compensation sweep runs synchronously inside failInstance(), itself invoked from the
        // async chargePayment-failure callback -- give it a moment to land after FAILED is visible.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                        statusOf(engine.stepsOf(instanceId), "reserveInventory"))
                .isEqualTo(StepInstanceStatus.COMPENSATED));

        var steps = engine.stepsOf(instanceId);

        // reserveInventory/notifyWarehouse implement CompensatingStepHandler -> real compensate() ran
        assertThat(statusOf(steps, "reserveInventory")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(steps, "notifyWarehouse")).isEqualTo(StepInstanceStatus.COMPENSATED);

        // validateOrder/reviewOrder/fork have no compensate() -> just cancelled, no side effect claimed
        assertThat(statusOf(steps, "validateOrder")).isEqualTo(StepInstanceStatus.CANCELLED);
        assertThat(statusOf(steps, "reviewOrder")).isEqualTo(StepInstanceStatus.CANCELLED);
        assertThat(statusOf(steps, "fork")).isEqualTo(StepInstanceStatus.CANCELLED);

        // chargePayment itself never COMPLETED -- it stays FAILED, not compensated
        assertThat(statusOf(steps, "chargePayment")).isEqualTo(StepInstanceStatus.FAILED);

        var finished = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(finished.getVariables()).containsEntry("inventoryReleased", true);
        assertThat(finished.getVariables()).containsEntry("warehouseCancellationNotified", true);

        var events = engine.eventsOf(instanceId);
        assertThat(events.stream().anyMatch(e -> e.getType() == EventType.STEP_COMPENSATED))
                .isTrue();
        assertThat(events.stream().anyMatch(e -> e.getType() == EventType.STEP_COMPENSATION_FAILED))
                .isFalse();
    }

    @Test
    void genericFailVariablePermanentlyFailsGenerateInvoiceAndCompensatesTheAlreadyCompletedParallelBranches() {
        // Every bundled demo handler honors a "<stepKey>Fail" boolean (see FailSwitch) -- this is
        // the generalized, per-step successor to the old v3 process version, which hardcoded
        // exactly this scenario (an always-broken generateInvoice handler) as a whole extra
        // deployable definition. Same scenario, driven by a start variable against plain v2 instead.
        engine.deploy("order-generic-fail-saga", DemoProcessDefinitions.v2());
        var instance = engine.start(
                "order-generic-fail-saga", Map.of("orderId", "ORD-GENERIC-FAIL", "generateInvoiceFail", true));
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                        statusOf(engine.stepsOf(instanceId), "chargePayment"))
                .isEqualTo(StepInstanceStatus.COMPENSATED));

        var steps = engine.stepsOf(instanceId);
        // all three fork branches ran to COMPLETED (unlike the chargePayment-fails test above) --
        // generateInvoiceFail only throws after the join, so every branch's compensate() runs
        assertThat(statusOf(steps, "chargePayment")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(steps, "reserveInventory")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(steps, "notifyWarehouse")).isEqualTo(StepInstanceStatus.COMPENSATED);
        assertThat(statusOf(steps, "generateInvoice")).isEqualTo(StepInstanceStatus.FAILED);

        var finished = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(finished.getVariables()).containsEntry("paymentRefunded", true);
        assertThat(finished.getVariables()).containsEntry("inventoryReleased", true);
        assertThat(finished.getVariables()).containsEntry("warehouseCancellationNotified", true);
    }

    @Test
    void genericFailVariableFailsValidateOrderInstantlyWithNothingYetToCompensate() {
        // validateOrder is the start step, maxAttempts=1 -- its fail switch demonstrates the other
        // end of the spectrum from the test above: a permanent failure before anything else ever ran.
        engine.deploy("order-validate-fail-saga", DemoProcessDefinitions.v1());
        var instance = engine.start(
                "order-validate-fail-saga", Map.of("orderId", "ORD-VALIDATE-FAIL", "validateOrderFail", true));
        var instanceId = instance.getId();

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);

        var steps = engine.stepsOf(instanceId);
        assertThat(statusOf(steps, "validateOrder")).isEqualTo(StepInstanceStatus.FAILED);
        assertThat(steps).noneMatch(s -> s.getStatus() == StepInstanceStatus.COMPLETED);
        assertThat(steps).noneMatch(s -> s.getStatus() == StepInstanceStatus.COMPENSATED);
    }

    private StepInstanceStatus statusOf(List<StepInstanceEntity> steps, String stepKey) {
        return steps.stream()
                .filter(s -> s.getStepKey().equals(stepKey))
                .reduce((first, second) -> second) // latest activation
                .orElseThrow(() -> new AssertionError("no step instance for " + stepKey))
                .getStatus();
    }

    private StepInstanceEntity awaitActiveUserTask(UUID instanceId, String stepKey) {
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
}
