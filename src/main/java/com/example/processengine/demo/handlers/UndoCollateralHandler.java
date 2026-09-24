package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("undoCollateral")
public class UndoCollateralHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(UndoCollateralHandler.class);

    private final SimulatedLatency latency;

    public UndoCollateralHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] undid collateral", ctx.getProcessInstanceId());
    }
}
