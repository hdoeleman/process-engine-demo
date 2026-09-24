package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.dto.StepSpec;
import com.example.processengine.dto.VariableSpec;
import org.junit.jupiter.api.Test;

/** Mirrors this session's Camunda {@code ActivateInsurance_Process} analysis: four guarded steps
 *  share one on-failure route (ADR-023), narrowed to the "ROLLBACK_ERROR" code (ADR-024) --
 *  matching the original Camunda process's own boundary error event -- and one step ({@code
 *  activateInsuranceAgi}) is deliberately left unguarded. */
class ActivateInsuranceProcessDefinitionsTest {

    private StepSpec step(String key) {
        return ActivateInsuranceProcessDefinitions.v1().steps().stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theFourGuardedStepsAllRouteToTheSharedRollbackSubProcessOnlyOnRollbackError() {
        for (String guarded :
                new String[] {"createCollateral", "createAsset", "updateCollateralStatus1", "updateCollateralStatus2"
                }) {
            assertThat(step(guarded).onFailureRouteStepKey()).as(guarded).isEqualTo("rollback");
            assertThat(step(guarded).onFailureRouteErrorCode()).as(guarded).isEqualTo("ROLLBACK_ERROR");
        }
    }

    @Test
    void activateInsuranceAgiIsDeliberatelyLeftUnguarded() {
        assertThat(step("activateInsuranceAgi").onFailureRouteStepKey()).isNull();
    }

    @Test
    void getInsuranceStatusAndLogEndAreAlsoUnguarded() {
        // Only the four steps with a real external side effect worth undoing are guarded -- a
        // status lookup and a final log line have nothing for the shared rollback to undo.
        assertThat(step("getInsuranceStatus").onFailureRouteStepKey()).isNull();
        assertThat(step("logEnd").onFailureRouteStepKey()).isNull();
    }

    @Test
    void rollbackIsASubProcessCallingTheDedicatedRollbackProcess() {
        var rollback = step("rollback");

        assertThat(rollback.type()).isEqualTo(StepType.SUB_PROCESS);
        assertThat(rollback.calledProcessKey()).isEqualTo(ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY);
    }

    @Test
    void theGatewayRoutesOnFullySignedWithActivateAsTheDefaultBranch() {
        var toEndNotSigned = ActivateInsuranceProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.from().equals("gateway") && t.to().equals("endNotSigned"))
                .findFirst()
                .orElseThrow();
        var toActivate = ActivateInsuranceProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.from().equals("gateway") && t.to().equals("activateInsuranceAgi"))
                .findFirst()
                .orElseThrow();

        assertThat(toEndNotSigned.condition()).isEqualTo("fullySigned == false");
        assertThat(toEndNotSigned.defaultBranchOrDefault()).isFalse();
        assertThat(toActivate.defaultBranchOrDefault()).isTrue();
    }

    @Test
    void declaresFullySignedAndOneFailSlowSwitchPerServiceTaskStep() {
        var names = ActivateInsuranceProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names)
                .containsExactlyInAnyOrder(
                        "fullySigned",
                        "getInsuranceStatusFail",
                        "getInsuranceStatusSlow",
                        "activateInsuranceAgiFail",
                        "activateInsuranceAgiSlow",
                        "createCollateralFail",
                        "createCollateralSlow",
                        "createCollateralFailErrorCode",
                        "createAssetFail",
                        "createAssetSlow",
                        "createAssetFailErrorCode",
                        "updateCollateralStatus1Fail",
                        "updateCollateralStatus1Slow",
                        "updateCollateralStatus1FailErrorCode",
                        "updateCollateralStatus2Fail",
                        "updateCollateralStatus2Slow",
                        "updateCollateralStatus2FailErrorCode",
                        "logEndFail",
                        "logEndSlow");
    }

    @Test
    void onlyTheFourGuardedStepsDeclareAFailErrorCodeSwitch() {
        var names = ActivateInsuranceProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .filter(n -> n.endsWith("FailErrorCode"))
                .toList();

        assertThat(names)
                .containsExactlyInAnyOrder(
                        "createCollateralFailErrorCode",
                        "createAssetFailErrorCode",
                        "updateCollateralStatus1FailErrorCode",
                        "updateCollateralStatus2FailErrorCode");
    }

    @Test
    void fullySignedDefaultsToTrue() {
        var fullySigned = ActivateInsuranceProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("fullySigned"))
                .findFirst()
                .orElseThrow();

        assertThat(fullySigned.defaultValue()).isEqualTo(true);
    }

    @Test
    void rollbackHasNoFailOrSlowSwitch() {
        // SUB_PROCESS, no handlerRef of its own for FailSwitch/PauseSwitch to hook into -- same
        // reasoning DemoProcessDefinitions documents for sendConfirmationEmail.
        var names = ActivateInsuranceProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names).doesNotContain("rollbackFail", "rollbackSlow");
    }

    @Test
    void everyDeclaredVariableHasAShortNonBlankDescription() {
        for (var v : ActivateInsuranceProcessDefinitions.v1().startVariablesOrEmpty()) {
            assertThat(v.description()).as("description of %s", v.name()).isNotBlank();
            assertThat(v.description().length())
                    .as("description length of %s", v.name())
                    .isLessThanOrEqualTo(60);
        }
    }
}
