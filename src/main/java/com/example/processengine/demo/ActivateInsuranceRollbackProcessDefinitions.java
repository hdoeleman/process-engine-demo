package com.example.processengine.demo;

import com.example.processengine.definition.StepEffect;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import java.util.List;

/** The flow {@code activate-insurance}'s four guarded steps route to on failure, after the engine has compensated the
 *  completed steps: a genuinely separate, independently deployable process, called via a SUB_PROCESS step rather than
 *  being a plain step there, mirroring {@code CustomerNotificationProcessDefinitions}'s own nesting precedent. It only
 *  logs, since the reversing is done by the compensations of the steps themselves. */
public final class ActivateInsuranceRollbackProcessDefinitions {

    public static final String PROCESS_KEY = "activate-insurance-rollback";

    private ActivateInsuranceRollbackProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Activate Insurance Rollback",
                "logRollback",
                List.of(
                        StepSpecs.serviceTask("logRollback", "Log rollback", "logRollback", StepEffect.NONE),
                        StepSpecs.end("end", "End")),
                List.of(TransitionSpecs.to("logRollback", "end")),
                List.of());
    }
}
