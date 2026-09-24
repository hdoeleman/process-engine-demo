package com.example.processengine.demo.handlers;

import com.example.processengine.engine.BusinessError;
import com.example.processengine.engine.StepExecutionContext;

/** Shared "make it fail" convention for every bundled demo handler: set a boolean start
 *  variable named "&lt;stepKey&gt;Fail" (e.g. "generateInvoiceFail") to make that step always
 *  throw, regardless of attempt count -- a permanent failure that demonstrates the saga
 *  compensation sweep unwinding whatever already completed. Generalizes what used to be a single
 *  hardcoded always-broken handler wired into a dedicated process version (v3): any step, in any
 *  deployed version, can now be made to fail the same way via a start variable alone.
 *
 *  <p>Also honors "&lt;stepKey&gt;FailErrorCode" (a string, set alongside "&lt;stepKey&gt;Fail"):
 *  if present, the thrown exception is a {@link BusinessError} carrying that code instead of a
 *  plain exception -- lets a demo exercise ADR-024's error-code-specific on-failure routing
 *  without every guarded handler needing its own bespoke throw. Absent, behavior is unchanged
 *  from before ADR-024 existed: a plain exception, matching no code-restricted route. */
final class FailSwitch {

    private FailSwitch() {}

    static void throwIfRequested(StepExecutionContext ctx) {
        var variable = ctx.getStepKey() + "Fail";
        if (!Boolean.TRUE.equals(ctx.getVariable(variable))) {
            return;
        }
        var message =
                "Simulated permanent failure for step '" + ctx.getStepKey() + "' (demo variable '" + variable + "')";
        var errorCode = (String) ctx.getVariable(ctx.getStepKey() + "FailErrorCode");
        if (errorCode != null) {
            throw new BusinessError(errorCode, message);
        }
        throw new RuntimeException(message);
    }
}
