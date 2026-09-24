package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reached only when {@code awaitQuoteOrTimeout}'s EVENT_BASED_GATEWAY (ADR-026) race resolves in
 *  favor of {@code quoteTimeout} -- nobody completed {@code recordQuoteManually} in time. See
 *  {@link LogManualQuoteHandler} for why this is its own dedicated handler rather than a shared
 *  convergence step. */
@Component("logQuoteTimeout")
public class LogQuoteTimeoutHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(LogQuoteTimeoutHandler.class);

    private final SimulatedLatency latency;

    public LogQuoteTimeoutHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        ctx.setVariable("outcome", "timeout");
        log.info(
                "[{}] supplier '{}' quote timed out -- no manual confirmation in time",
                ctx.getProcessInstanceId(),
                ctx.getVariable("miItem"));
    }
}
