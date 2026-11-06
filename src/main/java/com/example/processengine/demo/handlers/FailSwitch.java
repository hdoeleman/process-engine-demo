package com.example.processengine.demo.handlers;

import com.example.processengine.engine.BusinessError;
import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepNotPerformedException;

/** Shared "make it fail" convention for every bundled demo handler: set a boolean start
 *  variable named "&lt;stepKey&gt;Fail" (e.g. "generateInvoiceFail") to make that step fail on every
 *  attempt: a permanent failure that demonstrates the saga compensation sweep unwinding whatever
 *  already completed. Any step, in any deployed version, can be made to fail this way via a start
 *  variable alone.
 *
 *  <p>The simulated call never takes effect, so it throws {@link StepNotPerformedException}: the
 *  engine retries it while attempts remain, then the step fails. "&lt;stepKey&gt;FailErrorCode" (a
 *  string, set alongside "&lt;stepKey&gt;Fail") makes it a refusal instead, a {@link BusinessError}
 *  carrying that code, which fails the step at once and can take an error-code-specific on-failure
 *  route. "&lt;stepKey&gt;FailUnknown" set to true instead throws a plain exception, a failure that may
 *  have come after the effect: a step that is not idempotent then becomes AMBIGUOUS for an
 *  operator to resolve (ADR-030 in the engine). */
final class FailSwitch {

    private FailSwitch() {}

    static void throwIfRequested(StepExecutionContext ctx) {
        var stepKey = ctx.getStepKey();
        if (Boolean.TRUE.equals(ctx.getVariable(stepKey + "FailUnknown"))) {
            throw new IllegalStateException(
                    "Simulated lost answer for step '" + stepKey + "' (demo variable '" + stepKey + "FailUnknown')");
        }
        var variable = stepKey + "Fail";
        if (!Boolean.TRUE.equals(ctx.getVariable(variable))) {
            return;
        }
        var message = "Simulated permanent failure for step '" + stepKey + "' (demo variable '" + variable + "')";
        var errorCode = (String) ctx.getVariable(stepKey + "FailErrorCode");
        if (errorCode != null) {
            throw new BusinessError(errorCode, message);
        }
        throw new StepNotPerformedException(message);
    }
}
