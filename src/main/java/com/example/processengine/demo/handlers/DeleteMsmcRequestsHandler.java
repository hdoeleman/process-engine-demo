package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("deleteMsmcRequests")
public class DeleteMsmcRequestsHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DeleteMsmcRequestsHandler.class);

    private final SimulatedLatency latency;

    public DeleteMsmcRequestsHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] deleted msmc requests", ctx.getProcessInstanceId());
    }
}
