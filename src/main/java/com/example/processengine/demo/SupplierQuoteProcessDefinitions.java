package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpec;
import com.example.processengine.dto.VariableSpecs;
import java.util.List;

/** One instance per supplier, started by {@code multi-supplier-quote}'s multi-instance {@code
 *  requestQuotes} SUB_PROCESS step (ADR-027) -- never started directly by an operator. Showcases
 *  ADR-026's event-based gateway: {@code sendQuoteRequest} -> {@code awaitQuoteOrTimeout} races
 *  {@code recordQuoteManually} (a USER_TASK -- deliberately, not a RECEIVE_TASK, so it's completable
 *  straight from the Business User Console's Tasks table with no raw API call needed for a live
 *  demo) against {@code quoteTimeout} (a shortened-for-demo TIMER_WAIT, same {@code PT10S}
 *  convention {@code send-pdf}'s own {@code waitForSignature} already uses). Both branches converge
 *  on their own dedicated logging step (not a shared one -- see {@code LogManualQuoteHandler}'s own
 *  Javadoc for why) before reaching end. */
public final class SupplierQuoteProcessDefinitions {

    public static final String PROCESS_KEY = "supplier-quote";
    // Real-world duration would be hours; shortened here the same way send-pdf's own
    // waitForSignature is, so a live manual run actually resolves in seconds. See README.
    private static final String TIMEOUT_DURATION = "PT10S";

    private SupplierQuoteProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Supplier Quote",
                "sendQuoteRequest",
                List.of(
                        StepSpecs.serviceTask("sendQuoteRequest", "Send quote request", "sendQuoteRequest"),
                        StepSpecs.eventBasedGateway("awaitQuoteOrTimeout", "Await quote or timeout"),
                        StepSpecs.userTask("recordQuoteManually", "Record quote manually"),
                        StepSpecs.timerWait("quoteTimeout", "Quote timeout", TIMEOUT_DURATION),
                        StepSpecs.serviceTask("logManualQuote", "Log manual quote", "logManualQuote"),
                        StepSpecs.serviceTask("logQuoteTimeout", "Log quote timeout", "logQuoteTimeout"),
                        StepSpecs.end("end", "End")),
                List.of(
                        TransitionSpecs.to("sendQuoteRequest", "awaitQuoteOrTimeout"),
                        TransitionSpecs.to("awaitQuoteOrTimeout", "recordQuoteManually"),
                        TransitionSpecs.to("awaitQuoteOrTimeout", "quoteTimeout"),
                        TransitionSpecs.to("recordQuoteManually", "logManualQuote"),
                        TransitionSpecs.to("quoteTimeout", "logQuoteTimeout"),
                        TransitionSpecs.to("logManualQuote", "end"),
                        TransitionSpecs.to("logQuoteTimeout", "end")),
                startVariables());
    }

    private static List<VariableSpec> startVariables() {
        return List.of(
                VariableSpecs.bool("sendQuoteRequestFail", "Fails permanently"),
                VariableSpecs.bool("sendQuoteRequestSlow", "Pauses 11s (tests timeout + retry)"),
                VariableSpecs.bool("logManualQuoteFail", "Fails permanently"),
                VariableSpecs.bool("logManualQuoteSlow", "Pauses 11s (tests timeout + retry)"),
                VariableSpecs.bool("logQuoteTimeoutFail", "Fails permanently"),
                VariableSpecs.bool("logQuoteTimeoutSlow", "Pauses 11s (tests timeout + retry)"));
    }
}
