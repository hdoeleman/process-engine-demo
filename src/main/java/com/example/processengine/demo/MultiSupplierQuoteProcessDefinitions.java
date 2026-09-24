package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpec;
import com.example.processengine.dto.VariableSpecs;
import java.util.List;

/** Showcases ADR-027's multi-instance {@code SUB_PROCESS} fan-out together with ADR-026's
 *  event-based gateway (in the {@code supplier-quote} child it calls -- see {@link
 *  SupplierQuoteProcessDefinitions}): {@code requestQuotes} starts one {@code supplier-quote}
 *  child per item of the {@code suppliers} instance variable, each independently racing a rep's
 *  manual confirmation against a timeout, and once every child is done their outcomes are
 *  aggregated into {@code requestQuotesResults} for {@code summarizeQuotes} to report on.
 *
 * <p>{@code suppliers} is declared as a plain STRING {@link VariableSpec} (comma-separated names),
 * not a list -- {@link com.example.processengine.definition.VariableType} has no list-valued
 * widget, so {@code prepareRequest}'s own handler splits it into the real {@code List} {@code
 * requestQuotes} reads right after. See that handler's own Javadoc. */
public final class MultiSupplierQuoteProcessDefinitions {

    public static final String PROCESS_KEY = "multi-supplier-quote";

    private MultiSupplierQuoteProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Multi-Supplier Quote",
                "prepareRequest",
                List.of(
                        StepSpecs.serviceTask("prepareRequest", "Prepare request", "prepareRequest"),
                        StepSpecs.withMultiInstance(
                                StepSpecs.subProcess(
                                        "requestQuotes", "Request quotes", SupplierQuoteProcessDefinitions.PROCESS_KEY),
                                "suppliers"),
                        StepSpecs.serviceTask("summarizeQuotes", "Summarize quotes", "summarizeQuotes"),
                        StepSpecs.end("end", "End")),
                List.of(
                        TransitionSpecs.to("prepareRequest", "requestQuotes"),
                        TransitionSpecs.to("requestQuotes", "summarizeQuotes"),
                        TransitionSpecs.to("summarizeQuotes", "end")),
                startVariables());
    }

    private static List<VariableSpec> startVariables() {
        return List.of(
                VariableSpecs.text("suppliers", "Comma-separated supplier names", "Acme Corp, Globex, Initech"),
                VariableSpecs.bool("prepareRequestFail", "Fails permanently"),
                VariableSpecs.bool("prepareRequestSlow", "Pauses 11s (tests timeout + retry)"),
                VariableSpecs.bool("summarizeQuotesFail", "Fails permanently"),
                VariableSpecs.bool("summarizeQuotesSlow", "Pauses 11s (tests timeout + retry)"));
    }
}
