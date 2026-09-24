package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reached only when {@code awaitQuoteOrTimeout}'s EVENT_BASED_GATEWAY (ADR-026) race resolves in
 *  favor of {@code recordQuoteManually} -- a rep confirmed this supplier's quote came in before
 *  {@code quoteTimeout} elapsed. Sets {@code outcome} so it survives into this child's own final
 *  variable map, and from there into {@code multi-supplier-quote}'s aggregated {@code
 *  requestQuotesResults} once every sibling is done -- {@link LogQuoteTimeoutHandler} is the
 *  other, mutually exclusive branch's own dedicated handler, not a shared one, since a
 *  USER_TASK's own completion (via the Business User Console's Tasks table) carries no custom
 *  payload of its own to distinguish the branch from inside a single shared step. */
@Component("logManualQuote")
public class LogManualQuoteHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(LogManualQuoteHandler.class);

    private final SimulatedLatency latency;

    public LogManualQuoteHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        ctx.setVariable("outcome", "manual");
        log.info(
                "[{}] supplier '{}' quote recorded manually before the timeout",
                ctx.getProcessInstanceId(),
                ctx.getVariable("miItem"));
    }
}
