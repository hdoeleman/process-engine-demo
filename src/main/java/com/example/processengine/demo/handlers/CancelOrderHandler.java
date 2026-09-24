package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Compensating step: unwinds the payment/inventory/warehouse work done by the parallel branches. */
@Component("cancelOrder")
public class CancelOrderHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderHandler.class);

    private final SimulatedLatency latency;

    public CancelOrderHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] cancelled order (compensating charge/reservation/notification)", ctx.getProcessInstanceId());
        ctx.setVariable("cancelled", true);
    }
}
