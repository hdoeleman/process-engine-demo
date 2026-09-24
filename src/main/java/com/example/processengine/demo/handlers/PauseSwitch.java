package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;

/** Shared "make it slow" convention for every bundled demo handler: set a boolean start variable
 *  named "&lt;stepKey&gt;Slow" (e.g. "chargePaymentSlow") to make that step pause for {@link
 *  #PAUSE_MILLIS} before doing its normal work -- long enough to trip a deliberately short
 *  per-step timeout (set via the admin console's step-config editor) without waiting anywhere
 *  near the much larger engine-wide default, so the engine's timeout-then-retry path (see
 *  RetryBackoff) can be exercised on demand. Mirrors {@link FailSwitch}'s convention. */
final class PauseSwitch {

    static final long PAUSE_MILLIS = 11_000;

    private PauseSwitch() {}

    static void pauseIfRequested(StepExecutionContext ctx) throws InterruptedException {
        var variable = ctx.getStepKey() + "Slow";
        if (Boolean.TRUE.equals(ctx.getVariable(variable))) {
            Thread.sleep(PAUSE_MILLIS);
        }
    }
}
