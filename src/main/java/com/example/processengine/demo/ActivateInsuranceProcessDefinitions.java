package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpec;
import com.example.processengine.dto.VariableSpecs;
import java.util.ArrayList;
import java.util.List;

/** process-engine's own version of the Camunda {@code ActivateInsurance_Process} analyzed earlier
 *  this session: a linear chain of service tasks where four are guarded -- each routes (ADR-023,
 *  {@code StepSpecs#withOnFailureRoute}), and only on the "ROLLBACK_ERROR" code (ADR-024,
 *  matching the original Camunda process's own boundary error event), to one shared "rollback"
 *  SUB_PROCESS step instead of triggering the engine's default global saga compensation -- and
 *  one, {@code activateInsuranceAgi}, is deliberately left unguarded, so its permanent failure
 *  still demonstrates today's unchanged default behavior side by side with the routed alternative.
 *
 * <p>Every bundled handler honors the same "&lt;stepKey&gt;Fail"/"&lt;stepKey&gt;Slow" testing-switch
 * convention {@link DemoProcessDefinitions} already established (see {@code FailSwitch}/{@code
 * PauseSwitch}), plus FailSwitch's own "&lt;stepKey&gt;FailErrorCode" extension (ADR-024): setting
 * "createCollateralFail": true alone throws a plain exception -- a technical failure the
 * "ROLLBACK_ERROR"-restricted route does NOT match, so it still falls through to the default full
 * compensation, exactly like real Camunda leaving an unrelated exception uncaught by the boundary
 * event. Also setting "createCollateralFailErrorCode": "ROLLBACK_ERROR" is what actually
 * demonstrates the routed rollback path. "activateInsuranceAgiFail": true demonstrates the
 * unguarded default regardless of any error code, since that step has no route to match against
 * at all. "fullySigned" drives the gateway directly -- no handler needs to compute it. */
public final class ActivateInsuranceProcessDefinitions {

    public static final String PROCESS_KEY = "activate-insurance";
    private static final String ROLLBACK_ERROR_CODE = "ROLLBACK_ERROR";

    private ActivateInsuranceProcessDefinitions() {}

    private static final List<VariableSpec> BASE_VARIABLES =
            List.of(VariableSpecs.bool("fullySigned", "Whether the insurance is fully signed", true));

    /** One "make it fail permanently" switch and one "make it pause 11s" switch per service-task
     *  step key -- see FailSwitch/PauseSwitch. Mirrors {@code DemoProcessDefinitions
     *  #withTestingSwitches} (private there, so duplicated here rather than shared -- this
     *  project's own convention, same as its admin-console static JS files). */
    private static List<VariableSpec> withTestingSwitches(String... serviceTaskStepKeys) {
        List<VariableSpec> variables = new ArrayList<>(BASE_VARIABLES);
        for (String stepKey : serviceTaskStepKeys) {
            variables.add(VariableSpecs.bool(stepKey + "Fail", "Fails permanently"));
            variables.add(VariableSpecs.bool(stepKey + "Slow", "Pauses 11s (tests timeout + retry)"));
        }
        return variables;
    }

    /** One free-text "which error code to fail with" switch (ADR-024) per guarded step key --
     *  meaningless (never matches anything) on a step with no on-failure route, so declared only
     *  for the four guarded steps, not every step {@link #withTestingSwitches} covers. */
    private static List<VariableSpec> withFailErrorCodeSwitches(String... guardedStepKeys) {
        List<VariableSpec> variables = new ArrayList<>();
        for (String stepKey : guardedStepKeys) {
            variables.add(
                    VariableSpecs.text(stepKey + "FailErrorCode", "Error code to fail with (needs Fail set too)"));
        }
        return variables;
    }

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Activate Insurance",
                "getInsuranceStatus",
                List.of(
                        StepSpecs.serviceTask("getInsuranceStatus", "Get insurance status", "getInsuranceStatus"),
                        StepSpecs.exclusiveGateway("gateway", "fullySigned ?"),
                        StepSpecs.end("endNotSigned", "End (not signed)"),
                        StepSpecs.serviceTask(
                                "activateInsuranceAgi", "Activate insurance in AGI", "activateInsuranceAgi"),
                        onFailureRoutedServiceTask("createCollateral", "Create collateral in DBSUR"),
                        onFailureRoutedServiceTask("createAsset", "Create asset in DBBIEN"),
                        onFailureRoutedServiceTask("updateCollateralStatus1", "Update collateral status 1"),
                        onFailureRoutedServiceTask("updateCollateralStatus2", "Update collateral status 2"),
                        StepSpecs.serviceTask("logEnd", "Log end", "logEnd"),
                        StepSpecs.end("end", "End"),
                        StepSpecs.subProcess(
                                "rollback", "Rollback", ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY),
                        StepSpecs.end("rollbackEnd", "Rollback end")),
                List.of(
                        TransitionSpecs.to("getInsuranceStatus", "gateway"),
                        TransitionSpecs.when("gateway", "endNotSigned", "fullySigned == false"),
                        TransitionSpecs.defaultBranch("gateway", "activateInsuranceAgi"),
                        TransitionSpecs.to("activateInsuranceAgi", "createCollateral"),
                        TransitionSpecs.to("createCollateral", "createAsset"),
                        TransitionSpecs.to("createAsset", "updateCollateralStatus1"),
                        TransitionSpecs.to("updateCollateralStatus1", "updateCollateralStatus2"),
                        TransitionSpecs.to("updateCollateralStatus2", "logEnd"),
                        TransitionSpecs.to("logEnd", "end"),
                        TransitionSpecs.to("rollback", "rollbackEnd")),
                startVariables());
    }

    private static List<VariableSpec> startVariables() {
        List<VariableSpec> variables = new ArrayList<>(withTestingSwitches(
                "getInsuranceStatus",
                "activateInsuranceAgi",
                "createCollateral",
                "createAsset",
                "updateCollateralStatus1",
                "updateCollateralStatus2",
                "logEnd"));
        variables.addAll(withFailErrorCodeSwitches(
                "createCollateral", "createAsset", "updateCollateralStatus1", "updateCollateralStatus2"));
        return variables;
    }

    private static StepSpec onFailureRoutedServiceTask(String key, String name) {
        return StepSpecs.withOnFailureRoute(StepSpecs.serviceTask(key, name, key), "rollback", ROLLBACK_ERROR_CODE);
    }
}
