package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import org.junit.jupiter.api.Test;

class ActivateInsuranceRollbackProcessDefinitionsTest {

    @Test
    void isALinearChainOfServiceTasksEndingAtAnEndStep() {
        var spec = ActivateInsuranceRollbackProcessDefinitions.v1();

        assertThat(spec.startStepKey()).isEqualTo("undoCollateral");
        assertThat(spec.steps())
                .extracting(s -> s.key())
                .containsExactly("undoCollateral", "undoAsset", "logRollback", "end");
        assertThat(spec.steps().get(3).type()).isEqualTo(StepType.END);
    }

    @Test
    void declaresNoStartVariables() {
        // Never started directly by a user -- only reached via activate-insurance's own SUB_PROCESS
        // "rollback" step, the same as CustomerNotificationProcessDefinitions.
        assertThat(ActivateInsuranceRollbackProcessDefinitions.v1().startVariablesOrEmpty())
                .isEmpty();
    }
}
