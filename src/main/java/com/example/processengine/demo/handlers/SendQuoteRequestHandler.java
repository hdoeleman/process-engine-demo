package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** First step of {@code supplier-quote}, one instance per fan-out child (ADR-027) --
 *  {@code miItem} is the supplier name this particular child was started for, seeded onto every
 *  child's own variables by {@code requestQuotes}'s multi-instance activation. */
@Component("sendQuoteRequest")
public class SendQuoteRequestHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendQuoteRequestHandler.class);

    private final SimulatedLatency latency;

    public SendQuoteRequestHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] sent quote request to supplier '{}'", ctx.getProcessInstanceId(), ctx.getVariable("miItem"));
    }
}
