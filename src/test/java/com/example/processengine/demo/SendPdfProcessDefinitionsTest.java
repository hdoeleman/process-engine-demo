package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.dto.StepSpec;
import com.example.processengine.dto.VariableSpec;
import org.junit.jupiter.api.Test;

/** Mirrors this session's Camunda {@code SendPdf_Process} analysis: two guarded steps
 *  ({@code sendLeasingEmail}, {@code sendAgiEmail}) share one on-failure route (ADR-023),
 *  narrowed to the "ROLLBACK_ERROR" code (ADR-024) -- matching the real Camunda process's own
 *  boundary error events -- and {@code waitForSignature} is ADR-025's new TIMER_WAIT step type. */
class SendPdfProcessDefinitionsTest {

    private StepSpec step(String key) {
        return SendPdfProcessDefinitions.v1().steps().stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theTwoGuardedStepsAllRouteToTheSharedRollbackSubProcessOnlyOnRollbackError() {
        for (String guarded : new String[] {"sendLeasingEmail", "sendAgiEmail"}) {
            assertThat(step(guarded).onFailureRouteStepKey()).as(guarded).isEqualTo("rollback");
            assertThat(step(guarded).onFailureRouteErrorCode()).as(guarded).isEqualTo("ROLLBACK_ERROR");
        }
    }

    @Test
    void everyOtherServiceTaskIsUnguarded() {
        for (String unguarded : new String[] {"checkMsmcRequestId", "addEcarinaStatus", "checkFullySigned", "logEnd"}) {
            assertThat(step(unguarded).onFailureRouteStepKey()).as(unguarded).isNull();
        }
    }

    @Test
    void rollbackIsASubProcessCallingTheDedicatedSendPdfRollbackProcess() {
        var rollback = step("rollback");

        assertThat(rollback.type()).isEqualTo(StepType.SUB_PROCESS);
        assertThat(rollback.calledProcessKey()).isEqualTo(SendPdfRollbackProcessDefinitions.PROCESS_KEY);
    }

    @Test
    void waitForSignatureIsATimerWaitStepWithAShortenedDemoDuration() {
        var wait = step("waitForSignature");

        assertThat(wait.type()).isEqualTo(StepType.TIMER_WAIT);
        assertThat(wait.timerWait()).isEqualTo("PT10S");
    }

    @Test
    void everyGatewayHasExactlyOneConditionAndOneDefaultBranch() {
        // DefinitionValidator#collectGatewayErrors requires exactly one default -- unlike the real
        // Camunda source's own exhaustive-no-default gateways (a legitimate BPMN pattern this
        // engine's own validator doesn't accept, see this class's own... actually
        // SendPdfProcessDefinitions's Javadoc for why), so each gateway here is one condition plus
        // one default rather than two conditions.
        var transitions = SendPdfProcessDefinitions.v1().transitions();
        for (String gateway :
                new String[] {"typeGateway", "leasingGateway", "insuranceDemandGateway", "fullySignedGateway"}) {
            var outgoing =
                    transitions.stream().filter(t -> t.from().equals(gateway)).toList();
            assertThat(outgoing).as(gateway).hasSize(2);
            assertThat(outgoing.stream().filter(t -> t.defaultBranchOrDefault()).count())
                    .as(gateway)
                    .isEqualTo(1);
            assertThat(outgoing.stream()
                            .filter(t -> !t.defaultBranchOrDefault())
                            .allMatch(
                                    t -> t.condition() != null && !t.condition().isBlank()))
                    .as(gateway)
                    .isTrue();
        }
    }

    @Test
    void theBareMergePointIsDroppedInFavorOfDirectTransitionsToLogEnd() {
        // Gateway_1pfmbte in the source Camunda process (4 incoming, 1 unconditioned outgoing) has
        // no runtime role at all -- see SendPdfProcessDefinitions's own Javadoc. Each of its four
        // upstream steps transitions straight to logEnd instead.
        var toLogEnd = SendPdfProcessDefinitions.v1().transitions().stream()
                .filter(t -> t.to().equals("logEnd"))
                .map(t -> t.from())
                .toList();

        assertThat(toLogEnd)
                .containsExactlyInAnyOrder(
                        "sendLeasingEmail", "insuranceDemandGateway", "sendAgiEmail", "fullySignedGateway");
    }

    @Test
    void declaresTheFourDecisionVariablesAndOneFailSlowSwitchPerServiceTaskStep() {
        var names = SendPdfProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names)
                .containsExactlyInAnyOrder(
                        "type",
                        "isLeasing",
                        "insuranceDemandId",
                        "fullySigned",
                        "checkMsmcRequestIdFail",
                        "checkMsmcRequestIdSlow",
                        "addEcarinaStatusFail",
                        "addEcarinaStatusSlow",
                        "checkFullySignedFail",
                        "checkFullySignedSlow",
                        "sendLeasingEmailFail",
                        "sendLeasingEmailSlow",
                        "sendLeasingEmailFailErrorCode",
                        "sendAgiEmailFail",
                        "sendAgiEmailSlow",
                        "sendAgiEmailFailErrorCode",
                        "logEndFail",
                        "logEndSlow");
    }

    @Test
    void waitForSignatureHasNoFailOrSlowSwitch() {
        // TIMER_WAIT, never dispatched to a handler -- nothing for FailSwitch/PauseSwitch to hook into.
        var names = SendPdfProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names).doesNotContain("waitForSignatureFail", "waitForSignatureSlow");
    }

    @Test
    void everyDeclaredVariableHasAShortNonBlankDescription() {
        for (var v : SendPdfProcessDefinitions.v1().startVariablesOrEmpty()) {
            assertThat(v.description()).as("description of %s", v.name()).isNotBlank();
            assertThat(v.description().length())
                    .as("description length of %s", v.name())
                    .isLessThanOrEqualTo(60);
        }
    }
}
