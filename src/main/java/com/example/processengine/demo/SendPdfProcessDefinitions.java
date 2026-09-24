package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpec;
import com.example.processengine.dto.VariableSpecs;
import java.util.ArrayList;
import java.util.List;

/** process-engine's own version of the Camunda {@code SendPdf_Process} analyzed this session:
 * {@code checkMsmcRequestId} branches on {@code type} (credit vs. insurance) and, on the credit
 * branch, on {@code isLeasing}; both paths converge on {@code checkFullySigned}, which -- if
 * fully signed -- waits (ADR-025's new {@code TIMER_WAIT} step, {@code waitForSignature}) before
 * sending an AGI email. {@code sendLeasingEmail}/{@code sendAgiEmail} are guarded (ADR-023/024,
 * same "&lt;stepKey&gt;FailErrorCode" convention as {@link ActivateInsuranceProcessDefinitions}),
 * routing on the "ROLLBACK_ERROR" code to a shared "rollback" SUB_PROCESS step.
 *
 * <p>The source Camunda process modeled its two multi-way gateways (type: credit / insurance1 /
 * insurance2; the bare 4-incoming/1-outgoing merge feeding {@code logEnd}) with exhaustive,
 * no-default conditions and an unconditioned merge gateway -- both legitimate BPMN patterns, but
 * neither is expressible here: {@code DefinitionValidator#collectGatewayErrors} requires every
 * EXCLUSIVE_GATEWAY to have both &ge;2 outgoing transitions AND exactly one marked default (unlike
 * flowable-extensions' own structural validator, which allows exhaustive-no-default), and requires
 * &ge;2 outgoing transitions specifically -- a gateway used purely as a merge point (1 outgoing)
 * doesn't qualify as a gateway at all. Two mechanical, behavior-preserving translations follow from
 * that, neither a new capability: each gateway here declares one condition (the "special" branch)
 * plus one default (the "otherwise" branch, covering both insurance sub-types alike); and the bare
 * merge point is dropped entirely -- every step that fed it now transitions straight to {@code
 * logEnd}, which is exactly as valid as the merge node was (nothing here restricts a step's
 * <em>incoming</em> transition count, only outgoing).
 *
 * <p>Every bundled handler honors the same "&lt;stepKey&gt;Fail"/"&lt;stepKey&gt;Slow" testing-switch
 * convention {@link DemoProcessDefinitions} established, via {@link StepSpecs#withOnFailureRoute}
 * routing the two guarded steps to "rollback" only on "ROLLBACK_ERROR" (any other failure -- a
 * plain exception, or a different code -- falls through to the engine's default global saga
 * compensation instead, same as {@code activate-insurance}). {@code VariableSpec#defaultValue} is
 * UI-only metadata (pre-fills the admin console's "start instance" form) -- {@code
 * ProcessEngine#start} never merges it in, so a bare start with an empty variables map leaves
 * every decision variable genuinely absent from the instance's variable map, not merely null.
 * That matters here: process-engine's {@code MapAccessor}-backed SpEL evaluation throws ({@code
 * EL1008E: Property or field 'type' cannot be found}) on a condition referencing a variable that
 * was never set at all -- a stricter failure mode than "missing means null" -- so {@code
 * checkMsmcRequestId}/{@code checkFullySigned}'s own handlers default every decision variable
 * defensively before the next gateway can reference it (see their own Javadoc), the same reason
 * flowable-demo's delegates default theirs. A bare start reaches {@code checkMsmcRequestId} &rarr;
 * {@code typeGateway} ("credit", the when-branch) &rarr; {@code leasingGateway} (default) &rarr;
 * {@code insuranceDemandGateway} (default) &rarr; {@code checkFullySigned} &rarr; {@code
 * fullySignedGateway} (default) &rarr; {@code waitForSignature} &rarr; {@code sendAgiEmail} &rarr;
 * {@code logEnd} &rarr; {@code end}, exercising the new timer-wait feature out of the box. */
public final class SendPdfProcessDefinitions {

    public static final String PROCESS_KEY = "send-pdf";
    private static final String ROLLBACK_ERROR_CODE = "ROLLBACK_ERROR";
    // Real Camunda source specifies PT24H ("Wait 24h") -- shortened here, same as this project's
    // own demo failedJobRetryTimeCycle overrides elsewhere, so a live manual run actually resumes
    // in seconds instead of a day. See README.
    private static final String WAIT_DURATION = "PT10S";

    private SendPdfProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Send Pdf",
                "checkMsmcRequestId",
                List.of(
                        StepSpecs.serviceTask("checkMsmcRequestId", "Check msmc request id", "checkMsmcRequestId"),
                        StepSpecs.exclusiveGateway("typeGateway", "Type ?"),
                        StepSpecs.serviceTask("addEcarinaStatus", "Add ecarina status", "addEcarinaStatus"),
                        StepSpecs.exclusiveGateway("leasingGateway", "Leasing ?"),
                        onFailureRoutedServiceTask("sendLeasingEmail", "Send leasing email"),
                        StepSpecs.exclusiveGateway("insuranceDemandGateway", "Has insurance demand ?"),
                        StepSpecs.serviceTask("checkFullySigned", "Check fully signed", "checkFullySigned"),
                        StepSpecs.exclusiveGateway("fullySignedGateway", "Fully signed ?"),
                        StepSpecs.timerWait("waitForSignature", "Wait for signature", WAIT_DURATION),
                        onFailureRoutedServiceTask("sendAgiEmail", "Send AGI email"),
                        // Its own dedicated handler bean ("sendPdfLogEnd"), not a reuse of
                        // activate-insurance's own "logEnd" bean -- same step key is fine (scoped
                        // per definition), but sharing the handler would log a misleading
                        // "activate-insurance completed successfully" line for a send-pdf instance.
                        StepSpecs.serviceTask("logEnd", "Log end", "sendPdfLogEnd"),
                        StepSpecs.end("end", "End"),
                        StepSpecs.subProcess("rollback", "Rollback", SendPdfRollbackProcessDefinitions.PROCESS_KEY),
                        StepSpecs.end("rollbackEnd", "Rollback end")),
                List.of(
                        TransitionSpecs.to("checkMsmcRequestId", "typeGateway"),
                        TransitionSpecs.when("typeGateway", "leasingGateway", "type == 'credit'"),
                        TransitionSpecs.defaultBranch("typeGateway", "addEcarinaStatus"),
                        TransitionSpecs.to("addEcarinaStatus", "checkFullySigned"),
                        TransitionSpecs.when("leasingGateway", "sendLeasingEmail", "isLeasing == true"),
                        TransitionSpecs.defaultBranch("leasingGateway", "insuranceDemandGateway"),
                        TransitionSpecs.to("sendLeasingEmail", "logEnd"),
                        TransitionSpecs.when("insuranceDemandGateway", "logEnd", "insuranceDemandId == null"),
                        TransitionSpecs.defaultBranch("insuranceDemandGateway", "checkFullySigned"),
                        TransitionSpecs.to("checkFullySigned", "fullySignedGateway"),
                        TransitionSpecs.when("fullySignedGateway", "logEnd", "fullySigned == false"),
                        TransitionSpecs.defaultBranch("fullySignedGateway", "waitForSignature"),
                        TransitionSpecs.to("waitForSignature", "sendAgiEmail"),
                        TransitionSpecs.to("sendAgiEmail", "logEnd"),
                        TransitionSpecs.to("logEnd", "end"),
                        TransitionSpecs.to("rollback", "rollbackEnd")),
                startVariables());
    }

    private static List<VariableSpec> startVariables() {
        List<VariableSpec> variables = new ArrayList<>(List.of(
                VariableSpecs.text("type", "credit / insurance1 / insurance2", "credit"),
                VariableSpecs.bool("isLeasing", "Whether this is a leasing request", false),
                VariableSpecs.text("insuranceDemandId", "Insurance demand id, if any", "DEMAND-1"),
                VariableSpecs.bool("fullySigned", "Whether the request is fully signed", true)));
        variables.addAll(withTestingSwitches(
                "checkMsmcRequestId",
                "addEcarinaStatus",
                "checkFullySigned",
                "sendLeasingEmail",
                "sendAgiEmail",
                "logEnd"));
        variables.addAll(withFailErrorCodeSwitches("sendLeasingEmail", "sendAgiEmail"));
        return variables;
    }

    /** Mirrors {@link ActivateInsuranceProcessDefinitions}'s own private helper of the same name --
     *  duplicated rather than shared, this project's own established convention (see that class's
     *  Javadoc). Excludes {@code waitForSignature}: a TIMER_WAIT step is never dispatched to a
     *  handler, so it has no "make it fail/slow" switch to honor. */
    private static List<VariableSpec> withTestingSwitches(String... serviceTaskStepKeys) {
        List<VariableSpec> variables = new ArrayList<>();
        for (String stepKey : serviceTaskStepKeys) {
            variables.add(VariableSpecs.bool(stepKey + "Fail", "Fails permanently"));
            variables.add(VariableSpecs.bool(stepKey + "Slow", "Pauses 11s (tests timeout + retry)"));
        }
        return variables;
    }

    private static List<VariableSpec> withFailErrorCodeSwitches(String... guardedStepKeys) {
        List<VariableSpec> variables = new ArrayList<>();
        for (String stepKey : guardedStepKeys) {
            variables.add(
                    VariableSpecs.text(stepKey + "FailErrorCode", "Error code to fail with (needs Fail set too)"));
        }
        return variables;
    }

    private static StepSpec onFailureRoutedServiceTask(String key, String name) {
        return StepSpecs.withOnFailureRoute(StepSpecs.serviceTask(key, name, key), "rollback", ROLLBACK_ERROR_CODE);
    }
}
