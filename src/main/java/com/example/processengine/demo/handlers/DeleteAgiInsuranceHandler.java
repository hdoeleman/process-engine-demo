package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("deleteAgiInsurance")
public class DeleteAgiInsuranceHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DeleteAgiInsuranceHandler.class);

    private final SimulatedLatency latency;

    public DeleteAgiInsuranceHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] deleted AGI insurance", ctx.getProcessInstanceId());
    }
}
