package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpecs;
import java.util.List;

/** The shared rollback flow {@code send-pdf}'s two guarded steps route to on failure (ADR-023) --
 *  a genuinely separate process from {@code activate-insurance-rollback} (this session's real
 *  Camunda {@code Rollback_Process} export, a distinct domain: msmc requests / AGI insurance, not
 *  collateral/asset), deployed and called the same way. Kept small, same "purpose is demonstrating
 *  the on-failure route landing somewhere real, not rollback logic depth" reasoning {@link
 *  ActivateInsuranceRollbackProcessDefinitions} already documents -- no Fail/Slow testing switches
 *  here either, just the one {@code action} variable its own gateway actually branches on. */
public final class SendPdfRollbackProcessDefinitions {

    public static final String PROCESS_KEY = "send-pdf-rollback";

    private SendPdfRollbackProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Send Pdf Rollback",
                "checkErrorStatus",
                List.of(
                        StepSpecs.serviceTask("checkErrorStatus", "Check error status", "checkErrorStatus"),
                        StepSpecs.serviceTask("sendErrorEmail", "Send error email", "sendErrorEmail"),
                        StepSpecs.exclusiveGateway("actionGateway", "Action ?"),
                        StepSpecs.serviceTask("deleteMsmcRequests", "Delete msmc requests", "deleteMsmcRequests"),
                        StepSpecs.serviceTask("deleteAgiInsurance", "Delete AGI insurance", "deleteAgiInsurance"),
                        StepSpecs.end("end", "End"),
                        StepSpecs.end("endNoAction", "End (no action)")),
                List.of(
                        TransitionSpecs.to("checkErrorStatus", "sendErrorEmail"),
                        TransitionSpecs.to("sendErrorEmail", "actionGateway"),
                        TransitionSpecs.when("actionGateway", "deleteMsmcRequests", "action == 'cancel'"),
                        TransitionSpecs.defaultBranch("actionGateway", "endNoAction"),
                        TransitionSpecs.to("deleteMsmcRequests", "deleteAgiInsurance"),
                        TransitionSpecs.to("deleteAgiInsurance", "end")),
                List.of(VariableSpecs.text("action", "cancel / none", "cancel")));
    }
}
