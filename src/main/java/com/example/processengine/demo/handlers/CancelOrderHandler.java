package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The step on the declined-payment branch: cancels the order, reversing the payment, inventory and warehouse work of
 *  the parallel branches itself. An ordinary step, not the engine's compensation of those steps. Declared IDEMPOTENT:
 *  cancelling an order that is already cancelled changes nothing, so a call whose outcome is unknown is simply made
 *  again. It has no compensation: placing the order again would be a new order, which can fail. */
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
