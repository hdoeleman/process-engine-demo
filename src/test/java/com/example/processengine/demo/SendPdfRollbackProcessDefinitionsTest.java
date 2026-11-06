package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.dto.VariableSpec;
import org.junit.jupiter.api.Test;

/** Mirrors this session's real Camunda {@code Rollback_Process} export: a linear chain to a
 *  gateway on {@code action}, then either a delete chain to {@code end} or straight to a second,
 *  distinct end event ({@code endNoAction}) -- the exhaustive-two-condition gateway shape the
 *  source used couldn't be preserved as-is (see SendPdfProcessDefinitions's own Javadoc on
 *  DefinitionValidator's exactly-one-default requirement), so {@code action == 'cancel'} is the
 *  one declared condition and {@code endNoAction} is the default branch. */
class SendPdfRollbackProcessDefinitionsTest {

    @Test
    void isALinearChainToAGatewayWithTwoEndEvents() {
        var spec = SendPdfRollbackProcessDefinitions.v1();

        assertThat(spec.startStepKey()).isEqualTo("checkErrorStatus");
        assertThat(spec.steps())
                .extracting(s -> s.key())
                .containsExactly(
                        "checkErrorStatus",
                        "sendErrorEmail",
                        "actionGateway",
                        "deleteMsmcRequests",
                        "deleteAgiInsurance",
                        "end",
                        "endNoAction");
        assertThat(spec.steps().get(2).type()).isEqualTo(StepType.EXCLUSIVE_GATEWAY);
        assertThat(spec.steps().get(5).type()).isEqualTo(StepType.END);
        assertThat(spec.steps().get(6).type()).isEqualTo(StepType.END);
    }

    @Test
    void theGatewayRoutesOnActionCancelWithNoActionAsTheDefaultBranch() {
        var toDelete = SendPdfRollbackProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.from().equals("actionGateway") && t.to().equals("deleteMsmcRequests"))
                .findFirst()
                .orElseThrow();
        var toNoAction = SendPdfRollbackProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.from().equals("actionGateway") && t.to().equals("endNoAction"))
                .findFirst()
                .orElseThrow();

        assertThat(toDelete.condition()).isEqualTo("action == 'cancel'");
        assertThat(toDelete.defaultBranchOrDefault()).isFalse();
        assertThat(toNoAction.defaultBranchOrDefault()).isTrue();
    }

    @Test
    void declaresOnlyTheActionVariable() {
        // Never started directly by a user -- only reached via send-pdf's own SUB_PROCESS
        // "rollback" step, same reasoning ActivateInsuranceRollbackProcessDefinitions documents --
        // just the one variable its own gateway actually branches on, no Fail/Slow switches.
        var names = SendPdfRollbackProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names).containsExactly("action");
    }
}
