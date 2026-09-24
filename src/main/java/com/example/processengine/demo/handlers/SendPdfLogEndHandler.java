package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** send-pdf's own dedicated "log end" handler -- a separate bean from {@link LogEndHandler}
 *  (activate-insurance's own), so each bundled process keeps independent handlers even for a
 *  trivial, structurally-identical step. */
@Component("sendPdfLogEnd")
public class SendPdfLogEndHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendPdfLogEndHandler.class);

    private final SimulatedLatency latency;

    public SendPdfLogEndHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] send-pdf completed successfully", ctx.getProcessInstanceId());
    }
}
