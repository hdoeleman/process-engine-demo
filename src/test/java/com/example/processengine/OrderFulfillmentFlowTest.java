package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceEntity;
import com.example.processengine.runtime.StepInstanceRepository;
import com.example.processengine.runtime.StepInstanceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderFulfillmentFlowTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Autowired
    private StepInstanceRepository stepInstanceRepo;

    @Test
    void approvedOrderRunsParallelBranchesThenGeneratesInvoice() {
        engine.deploy("order-approved", DemoProcessDefinitions.v1());
        var instance = engine.start("order-approved", Map.of("orderId", "ORD-1"));
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        var finished = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(finished.getVariables()).containsEntry("paymentApproved", true);
        assertThat(finished.getVariables()).containsKey("invoiceId");
        assertThat(finished.getVariables()).doesNotContainKey("cancelled");

        var steps = engine.stepsOf(instanceId);
        assertThat(stepKeysWithStatus(steps, StepInstanceStatus.COMPLETED))
                .contains(
                        "validateOrder",
                        "reviewOrder",
                        "fork",
                        "chargePayment",
                        "reserveInventory",
                        "notifyWarehouse",
                        "join",
                        "paymentGateway",
                        "generateInvoice",
                        "end");
        assertThat(stepKeysWithStatus(steps, StepInstanceStatus.COMPLETED)).doesNotContain("cancelOrder");

        // once the instance is COMPLETED, no step instance may be left stuck WAITING (the join is
        // reached by three branches but must reuse one row, not leave two behind forever)
        assertThat(stepKeysWithStatus(steps, StepInstanceStatus.WAITING)).isEmpty();
        assertThat(steps.stream().filter(s -> s.getStepKey().equals("join")).count())
                .isEqualTo(1);
    }

    @Test
    void declinedPaymentRoutesToCancelOrder() {
        engine.deploy("order-declined", DemoProcessDefinitions.v1());
        var instance = engine.start("order-declined", Map.of("orderId", "ORD-2", "simulateDeclined", true));
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        var finished = instanceRepo.findById(instanceId).orElseThrow();
        assertThat(finished.getVariables()).containsEntry("paymentApproved", false);
        assertThat(finished.getVariables()).containsEntry("cancelled", true);
        assertThat(finished.getVariables()).doesNotContainKey("invoiceId");
    }

    /** cancelOrder is idempotent: cancelling an order that is already cancelled changes nothing, so a call whose outcome
     *  is unknown is tried again while attempts remain. Once they are used up, the cancellation may still have
     *  happened, so nothing is compensated yet: an operator finds out. Resolved as failed, the order is compensated. */
    @Test
    void anUnknownOutcomeOfCancelOrderIsLeftForAnOperatorAndResolvingItAsFailedCompensatesTheOrder() {
        engine.deploy("order-cancel-unknown", DemoProcessDefinitions.v1());
        var instance = engine.start(
                "order-cancel-unknown",
                Map.of("orderId", "ORD-4", "simulateDeclined", true, "cancelOrderFailUnknown", true));
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        var cancelOrder = awaitStep(instanceId, "cancelOrder", StepInstanceStatus.OUTCOME_UNKNOWN);
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(stepKeysWithStatus(engine.stepsOf(instanceId), StepInstanceStatus.COMPENSATED))
                .isEmpty();

        engine.resolveOutcome(instanceId, cancelOrder.getId(), false, Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.FAILED);
        assertThat(stepKeysWithStatus(engine.stepsOf(instanceId), StepInstanceStatus.COMPENSATED))
                .containsExactlyInAnyOrder("chargePayment", "reserveInventory", "notifyWarehouse");
    }

    private StepInstanceEntity awaitStep(UUID instanceId, String stepKey, StepInstanceStatus status) {
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(engine.stepsOf(instanceId))
                        .anyMatch(s -> s.getStepKey().equals(stepKey) && s.getStatus() == status));
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey) && s.getStatus() == status)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void chargePaymentRetriesOnTransientFailureThenSucceeds() {
        engine.deploy("order-retry", DemoProcessDefinitions.v1());
        var instance = engine.start(
                "order-retry",
                Map.of(
                        "orderId",
                        "ORD-3",
                        "chargePaymentFailAttempts",
                        2)); // fails attempts 1 & 2, succeeds on 3 (maxAttempts=3)
        var instanceId = instance.getId();

        var reviewTask = awaitActiveUserTask(instanceId, "reviewOrder");
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        var steps = stepInstanceRepo.findByProcessInstance_IdOrderByActivatedAtAsc(instanceId);
        var chargePayment = steps.stream()
                .filter(s -> s.getStepKey().equals("chargePayment"))
                .findFirst()
                .orElseThrow();
        assertThat(chargePayment.getAttempt()).isEqualTo(3);
        assertThat(chargePayment.getStatus()).isEqualTo(StepInstanceStatus.COMPLETED);
    }

    private StepInstanceEntity awaitActiveUserTask(UUID instanceId, String stepKey) {
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, status -> true); // ensure instance exists
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var found = engine.stepsOf(instanceId).stream()
                            .anyMatch(
                                    s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE);
                    assertThat(found).isTrue();
                });
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
    }

    private List<String> stepKeysWithStatus(List<StepInstanceEntity> steps, StepInstanceStatus status) {
        return steps.stream()
                .filter(s -> s.getStatus() == status)
                .map(StepInstanceEntity::getStepKey)
                .toList();
    }
}
