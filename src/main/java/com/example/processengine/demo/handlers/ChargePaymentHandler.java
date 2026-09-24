package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demonstrates both failure modes a step can have:
 *  - transient/retryable: controlled by "chargePaymentFailAttempts" -- throws until that many
 *    attempts have been made, then succeeds. Exercises the engine's retry-with-backoff path.
 *  - business decision: controlled by "simulateDeclined" -- the charge call itself succeeds,
 *    but sets paymentApproved=false, which the "Payment ok?" gateway routes on. Not a failure.
 *
 * <p>Implements {@link CompensatingStepHandler}: a real charge is exactly the kind of external
 * side effect a saga rollback needs to undo -- if a later step in the same instance fails
 * permanently, or an operator rewinds past this step, the charge must be refunded.
 */
@Component("chargePayment")
public class ChargePaymentHandler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargePaymentHandler.class);

    private final SimulatedLatency latency;

    public ChargePaymentHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        var failAttempts = asInt(ctx.getVariable("chargePaymentFailAttempts"), 0);
        if (ctx.getAttempt() <= failAttempts) {
            throw new RuntimeException("Simulated payment gateway timeout (attempt " + ctx.getAttempt() + ")");
        }
        var declined = Boolean.TRUE.equals(ctx.getVariable("simulateDeclined"));
        log.info("[{}] charged payment, approved={}", ctx.getProcessInstanceId(), !declined);
        ctx.setVariable("paymentCharged", true);
        ctx.setVariable("paymentApproved", !declined);
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        // Tolerates being called against a charge that was never actually approved/captured (e.g.
        // a declined payment still got this far in the graph) -- an operator-triggered retry of a
        // failed compensation calls this again, so it must be safe to repeat.
        log.info("[{}] refunded payment", ctx.getProcessInstanceId());
        ctx.setVariable("paymentCharged", false);
        ctx.setVariable("paymentRefunded", true);
    }

    private int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
