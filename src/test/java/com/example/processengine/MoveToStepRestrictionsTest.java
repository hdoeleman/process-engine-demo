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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the restrictions {@link ProcessEngine#moveToStep} enforces beyond "instance not KILLED":
 * targets must be graph-ancestors of everything currently in flight (backward-only), and may never
 * land inside a parallel fork/join's interior (including the join itself) -- this is what closes the
 * stale-parallel-join bug both AI reviews flagged around jumping into the middle of a fork/join region.
 */
@SpringBootTest
@ActiveProfiles("test")
class MoveToStepRestrictionsTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void forwardMoveWhileMidFlightIsRejected() {
        engine.deploy("order-restrict-fwd", DemoProcessDefinitions.v1());
        var instance = engine.start("order-restrict-fwd", Map.of("orderId", "ORD-R1"));
        var instanceId = instance.getId();

        // instance is parked at reviewOrder (ACTIVE); generateInvoice is downstream of it, so jumping
        // there would be a forward move, not a rewind -- must be rejected.
        awaitActive(instanceId, "reviewOrder");

        assertThatThrownBy(() -> engine.moveToStep(instanceId, "generateInvoice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("move-to-step only allows");

        // rejection must not have mutated instance state
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.RUNNING);
    }

    @Test
    void movingToParallelJoinItselfIsRejected() {
        engine.deploy("order-restrict-join", DemoProcessDefinitions.v1());
        var instance = engine.start("order-restrict-join", Map.of("orderId", "ORD-R2"));
        var instanceId = instance.getId();

        var review = awaitActive(instanceId, "reviewOrder");
        engine.completeStep(instanceId, review.getId(), Map.of());
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        assertThatThrownBy(() -> engine.moveToStep(instanceId, "join"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parallel");
    }

    @Test
    void movingIntoAParallelBranchInteriorIsRejected() {
        engine.deploy("order-restrict-fork-interior", DemoProcessDefinitions.v1());
        var instance = engine.start("order-restrict-fork-interior", Map.of("orderId", "ORD-R3"));
        var instanceId = instance.getId();

        var review = awaitActive(instanceId, "reviewOrder");
        engine.completeStep(instanceId, review.getId(), Map.of());
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        // reserveInventory is one of the three parallel branches between fork and join -- landing there
        // alone would leave the other two branches' completion state inconsistent with a single active
        // branch, so it must be rejected same as the join itself.
        assertThatThrownBy(() -> engine.moveToStep(instanceId, "reserveInventory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parallel");
    }

    @Test
    void movingToTheForkStepItselfResetsAndRerunsTheWholeParallelRegionCleanly() {
        engine.deploy("order-restrict-fork-target", DemoProcessDefinitions.v1());
        var instance = engine.start("order-restrict-fork-target", Map.of("orderId", "ORD-R4"));
        var instanceId = instance.getId();

        var review = awaitActive(instanceId, "reviewOrder");
        engine.completeStep(instanceId, review.getId(), Map.of());
        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        // the fork node itself is a legitimate target (it's the ancestor of the whole parallel region,
        // not an interior node) -- moving there must re-run all three branches from scratch with no
        // stale WAITING rows or duplicate join activations left behind.
        engine.moveToStep(instanceId, "fork");
        assertThat(instanceRepo.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.RUNNING);

        EngineTestSupport.awaitStatus(instanceRepo, instanceId, InstanceStatus.COMPLETED);

        var steps = engine.stepsOf(instanceId);
        assertThat(countActivationsOf(steps, "join"))
                .isEqualTo(2); // once pre-jump, once post-jump -- not stale/duplicated within either run
        assertThat(countActivationsOf(steps, "chargePayment")).isEqualTo(2); // once pre-jump, once post-jump
        assertThat(countActivationsOf(steps, "reserveInventory")).isEqualTo(2);
        assertThat(countActivationsOf(steps, "notifyWarehouse")).isEqualTo(2);

        assertThat(steps.stream().anyMatch(s -> s.getStatus() == StepInstanceStatus.WAITING))
                .isFalse();
        assertThat(steps.stream().anyMatch(s -> s.getStatus() == StepInstanceStatus.ACTIVE))
                .isFalse();
    }

    private long countActivationsOf(List<StepInstanceEntity> steps, String stepKey) {
        return steps.stream().filter(s -> s.getStepKey().equals(stepKey)).count();
    }

    private StepInstanceEntity awaitActive(UUID instanceId, String stepKey) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var found = engine.stepsOf(instanceId).stream()
                    .anyMatch(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE);
            assertThat(found).isTrue();
        });
        return engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals(stepKey) && s.getStatus() == StepInstanceStatus.ACTIVE)
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
