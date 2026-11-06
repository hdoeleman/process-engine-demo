package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.dto.StepSpec;
import org.junit.jupiter.api.Test;

class SupplierQuoteProcessDefinitionsTest {

    private StepSpec step(String key) {
        return SupplierQuoteProcessDefinitions.v1().steps().stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void awaitQuoteOrTimeoutIsAnEventBasedGatewayRacingTwoBranches() {
        var gateway = step("awaitQuoteOrTimeout");

        assertThat(gateway.type()).isEqualTo(StepType.EVENT_BASED_GATEWAY);
        var outgoing = SupplierQuoteProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.from().equals("awaitQuoteOrTimeout"))
                .map(t -> t.to())
                .toList();
        assertThat(outgoing).containsExactlyInAnyOrder("recordQuoteManually", "quoteTimeout");
    }

    @Test
    void recordQuoteManuallyIsAUserTaskNotAReceiveTask() {
        // Deliberately USER_TASK -- see this class's own Javadoc: completable straight from the
        // Business User Console's Tasks table, no raw API call needed for a live demo.
        assertThat(step("recordQuoteManually").type()).isEqualTo(StepType.USER_TASK);
    }

    @Test
    void quoteTimeoutIsATimerWaitStepWithAShortenedDemoDuration() {
        var timeout = step("quoteTimeout");

        assertThat(timeout.type()).isEqualTo(StepType.TIMER_WAIT);
        assertThat(timeout.timerWait()).isEqualTo("PT10S");
    }

    @Test
    void bothRaceBranchesConvergeOnTheirOwnDedicatedLoggingStepBeforeEnd() {
        var transitions = SupplierQuoteProcessDefinitions.v1().transitions();

        assertThat(transitions.stream()
                        .filter(t -> t.from().equals("recordQuoteManually"))
                        .map(t -> t.to()))
                .containsExactly("logManualQuote");
        assertThat(transitions.stream()
                        .filter(t -> t.from().equals("quoteTimeout"))
                        .map(t -> t.to()))
                .containsExactly("logQuoteTimeout");
        assertThat(transitions.stream()
                        .filter(t ->
                                t.from().equals("logManualQuote") || t.from().equals("logQuoteTimeout"))
                        .map(t -> t.to()))
                .containsExactly("end", "end");
    }
}
