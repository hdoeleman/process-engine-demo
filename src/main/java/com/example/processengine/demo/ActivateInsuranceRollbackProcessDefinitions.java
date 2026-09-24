package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import java.util.List;

/** The shared rollback flow {@code activate-insurance}'s four guarded steps route to on failure
 *  (ADR-023) -- a genuinely separate, independently-deployable process, called via a SUB_PROCESS
 *  step rather than being a plain step there, mirroring {@code
 *  CustomerNotificationProcessDefinitions}'s own nesting precedent. Kept deliberately small (undo
 *  collateral, undo asset, log) since its purpose is demonstrating the on-failure route landing
 *  somewhere real, not rollback logic depth. */
public final class ActivateInsuranceRollbackProcessDefinitions {

    public static final String PROCESS_KEY = "activate-insurance-rollback";

    private ActivateInsuranceRollbackProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Activate Insurance Rollback",
                "undoCollateral",
                List.of(
                        StepSpecs.serviceTask("undoCollateral", "Undo collateral", "undoCollateral"),
                        StepSpecs.serviceTask("undoAsset", "Undo asset", "undoAsset"),
                        StepSpecs.serviceTask("logRollback", "Log rollback", "logRollback"),
                        StepSpecs.end("end", "End")),
                List.of(
                        TransitionSpecs.to("undoCollateral", "undoAsset"),
                        TransitionSpecs.to("undoAsset", "logRollback"),
                        TransitionSpecs.to("logRollback", "end")),
                List.of());
    }
}
