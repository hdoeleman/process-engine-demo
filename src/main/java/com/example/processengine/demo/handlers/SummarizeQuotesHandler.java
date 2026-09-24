package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Last step of {@code multi-supplier-quote}, right after {@code requestQuotes}'s multi-instance
 *  SUB_PROCESS step (ADR-027) merges every {@code supplier-quote} child's outcome into {@code
 *  requestQuotesResults} -- just logs the aggregated list, since demonstrating that aggregation is
 *  this step's whole purpose, not summarization logic depth. */
@Component("summarizeQuotes")
public class SummarizeQuotesHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeQuotesHandler.class);

    private final SimulatedLatency latency;

    public SummarizeQuotesHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info(
                "[{}] summarized supplier quotes: {}",
                ctx.getProcessInstanceId(),
                ctx.getVariable("requestQuotesResults"));
    }
}
