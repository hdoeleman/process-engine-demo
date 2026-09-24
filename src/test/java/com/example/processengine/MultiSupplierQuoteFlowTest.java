package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.demo.MultiSupplierQuoteProcessDefinitions;
import com.example.processengine.demo.SupplierQuoteProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceEntity;
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

/** End-to-end coverage of the bundled multi-supplier-quote/supplier-quote pair -- ADR-027's
 *  multi-instance SUB_PROCESS fan-out and ADR-026's event-based gateway, both actually running
 *  through the real Spring context/scheduler (the periodic sub-process and timer-wait scanners),
 *  same reasoning and shape as {@code SendPdfFlowTest}. */
@SpringBootTest
@ActiveProfiles("test")
class MultiSupplierQuoteFlowTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    private void deployMultiSupplierQuote() {
        engine.deploy(SupplierQuoteProcessDefinitions.PROCESS_KEY, SupplierQuoteProcessDefinitions.v1());
        engine.deploy(MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, MultiSupplierQuoteProcessDefinitions.v1());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> awaitResults(UUID instanceId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(25))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(
                                instanceRepo.findById(instanceId).orElseThrow().getStatus())
                        .isEqualTo(InstanceStatus.COMPLETED));
        return (List<Map<String, Object>>)
                instanceRepo.findById(instanceId).orElseThrow().getVariables().get("requestQuotesResults");
    }

    @Test
    void everySupplierTimingOutStillReachesEndWithAThreeEntryResultsList() {
        deployMultiSupplierQuote();

        var instance = engine.start(
                MultiSupplierQuoteProcessDefinitions.PROCESS_KEY,
                Map.of("suppliers", List.of("Acme Corp", "Globex", "Initech")));

        var results = awaitResults(instance.getId());

        assertThat(results).hasSize(3);
        assertThat(results).allSatisfy(r -> assertThat(r).containsEntry("outcome", "timeout"));
        assertThat(results.stream().map(r -> r.get("miItem")))
                .containsExactlyInAnyOrder("Acme Corp", "Globex", "Initech");
    }

    /** {@code suppliers} arrives as a plain comma-separated string from the Business User
     *  Console's "New instance" form (no list-valued widget exists -- see {@code
     *  PrepareRequestHandler}'s own Javadoc), not the {@code List} the other tests here pass
     *  directly -- confirms {@code prepareRequest} actually splits it into real fan-out items. */
    @Test
    void suppliersArrivingAsACommaSeparatedStringStillFansOutCorrectly() {
        deployMultiSupplierQuote();

        var instance = engine.start(
                MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, Map.of("suppliers", "Acme Corp, Globex"));

        var results = awaitResults(instance.getId());

        assertThat(results.stream().map(r -> r.get("miItem"))).containsExactlyInAnyOrder("Acme Corp", "Globex");
    }

    /** Completing the single child's recordQuoteManually task immediately, well before its 10s
     *  quoteTimeout would elapse, resolves the race in favor of the USER_TASK branch -- the
     *  timer branch is cancelled (ADR-026) and never fires, and the aggregated result reflects
     *  the manual outcome instead of a timeout. */
    @Test
    void completingRecordQuoteManuallyBeforeTheTimeoutWinsTheRace() {
        deployMultiSupplierQuote();

        var instance = engine.start(
                MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, Map.of("suppliers", List.of("Acme Corp")));

        var requestQuotesStepHolder = new StepInstanceEntity[1];
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var found = engine.stepsOf(instance.getId()).stream()
                    .filter(s -> s.getStepKey().equals("requestQuotes"))
                    .findFirst();
            assertThat(found).isPresent();
            requestQuotesStepHolder[0] = found.orElseThrow();
        });
        var requestQuotesStep = requestQuotesStepHolder[0];
        var childHolder = new ProcessInstanceEntity[1];
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var found = instanceRepo.findByParentStepInstanceId(requestQuotesStep.getId());
            assertThat(found).isNotEmpty();
            childHolder[0] = found.get(0);
        });
        var child = childHolder[0];
        var stepHolder = new StepInstanceEntity[1];
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var found = engine.stepsOf(child.getId()).stream()
                    .filter(s ->
                            s.getStepKey().equals("recordQuoteManually") && s.getStatus() == StepInstanceStatus.ACTIVE)
                    .findFirst();
            assertThat(found).isPresent();
            stepHolder[0] = found.orElseThrow();
        });

        engine.completeStep(child.getId(), stepHolder[0].getId(), Map.of());

        var results = awaitResults(instance.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("outcome", "manual").containsEntry("miItem", "Acme Corp");
        var timeoutStep = engine.stepsOf(child.getId()).stream()
                .filter(s -> s.getStepKey().equals("quoteTimeout"))
                .findFirst()
                .orElseThrow();
        assertThat(timeoutStep.getStatus()).isEqualTo(StepInstanceStatus.CANCELLED);
    }

    @Test
    void requestQuotesIsAMultiInstanceSubProcessStep() {
        deployMultiSupplierQuote();

        var definition = engine.get(engine.start(
                                MultiSupplierQuoteProcessDefinitions.PROCESS_KEY,
                                Map.of("suppliers", List.<String>of()))
                        .getId())
                .getProcessDefinition();

        var requestQuotes = definition.requireStep("requestQuotes");
        assertThat(requestQuotes.getType()).isEqualTo(StepType.SUB_PROCESS);
        assertThat(requestQuotes.getMultiInstanceCollectionVariable()).isEqualTo("suppliers");
    }
}
