package com.example.processengine.demo.handlers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.processengine.engine.BusinessError;
import com.example.processengine.engine.StepExecutionContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FailSwitchTest {

    @Test
    void doesNothingWhenTheFailSwitchIsNotSet() {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "createCollateral", 1, Map.of());

        assertThatCode(() -> FailSwitch.throwIfRequested(ctx)).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenTheFailSwitchIsFalse() {
        var ctx = new StepExecutionContext(
                UUID.randomUUID(), "createCollateral", 1, Map.of("createCollateralFail", false));

        assertThatCode(() -> FailSwitch.throwIfRequested(ctx)).doesNotThrowAnyException();
    }

    @Test
    void throwsAPlainExceptionWhenNoErrorCodeIsGiven() {
        var ctx = new StepExecutionContext(
                UUID.randomUUID(), "createCollateral", 1, Map.of("createCollateralFail", true));

        assertThatThrownBy(() -> FailSwitch.throwIfRequested(ctx))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(BusinessError.class)
                .hasMessageContaining("createCollateral");
    }

    @Test
    void throwsABusinessErrorCarryingTheGivenCodeWhenFailErrorCodeIsSet() {
        var ctx = new StepExecutionContext(
                UUID.randomUUID(),
                "createCollateral",
                1,
                Map.of("createCollateralFail", true, "createCollateralFailErrorCode", "ROLLBACK_ERROR"));

        assertThatThrownBy(() -> FailSwitch.throwIfRequested(ctx))
                .isInstanceOf(BusinessError.class)
                .extracting(e -> ((BusinessError) e).getCode())
                .isEqualTo("ROLLBACK_ERROR");
    }

    @Test
    void onlyThatStepsOwnFailVariableTriggersIt() {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "createCollateral", 1, Map.of("createAssetFail", true));

        assertThatCode(() -> FailSwitch.throwIfRequested(ctx)).doesNotThrowAnyException();
    }

    @Test
    void theErrorCodeVariableAloneWithNoFailSwitchDoesNothing() {
        var ctx = new StepExecutionContext(
                UUID.randomUUID(), "createCollateral", 1, Map.of("createCollateralFailErrorCode", "ROLLBACK_ERROR"));

        assertThatCode(() -> FailSwitch.throwIfRequested(ctx)).doesNotThrowAnyException();
    }
}
