package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implements {@link CompensatingStepHandler}: the warehouse acted on this notification (e.g.
 *  started staging the order), so a saga rollback or operator rewind must tell it to stand down. */
@Component("notifyWarehouse")
public class NotifyWarehouseHandler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyWarehouseHandler.class);

    private final SimulatedLatency latency;

    public NotifyWarehouseHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] notified warehouse", ctx.getProcessInstanceId());
        ctx.setVariable("warehouseNotified", true);
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        log.info("[{}] notified warehouse of cancellation", ctx.getProcessInstanceId());
        ctx.setVariable("warehouseNotified", false);
        ctx.setVariable("warehouseCancellationNotified", true);
    }
}
