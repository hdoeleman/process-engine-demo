package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implements {@link CompensatingStepHandler}: a reserved inventory unit is an external side
 *  effect held against a warehouse system, so a saga rollback or operator rewind must release it. */
@Component("reserveInventory")
public class ReserveInventoryHandler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(ReserveInventoryHandler.class);

    private final SimulatedLatency latency;

    public ReserveInventoryHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] reserved inventory", ctx.getProcessInstanceId());
        ctx.setVariable("inventoryReserved", true);
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        log.info("[{}] released inventory reservation", ctx.getProcessInstanceId());
        ctx.setVariable("inventoryReserved", false);
        ctx.setVariable("inventoryReleased", true);
    }
}
