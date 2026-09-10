package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;

/** Shared "make it fail" convention for every bundled demo handler: set a boolean start
 *  variable named "&lt;stepKey&gt;Fail" (e.g. "generateInvoiceFail") to make that step always
 *  throw, regardless of attempt count -- a permanent failure that demonstrates the saga
 *  compensation sweep unwinding whatever already completed. Generalizes what used to be a single
 *  hardcoded always-broken handler wired into a dedicated process version (v3): any step, in any
 *  deployed version, can now be made to fail the same way via a start variable alone. */
final class FailSwitch {

    private FailSwitch() {}

    static void throwIfRequested(StepExecutionContext ctx) {
        var variable = ctx.getStepKey() + "Fail";
        if (Boolean.TRUE.equals(ctx.getVariable(variable))) {
            throw new RuntimeException("Simulated permanent failure for step '" + ctx.getStepKey()
                    + "' (demo variable '" + variable + "')");
        }
    }
}
