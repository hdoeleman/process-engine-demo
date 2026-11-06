package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("validateOrder")
public class ValidateOrderHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidateOrderHandler.class);

    private final SimulatedLatency latency;

    public ValidateOrderHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        var orderId = ctx.getVariable("orderId");
        if (orderId == null) {
            throw new IllegalArgumentException("orderId variable is required to validate an order");
        }
        log.info("[{}] validated order {}", ctx.getProcessInstanceId(), orderId);
        ctx.setVariable("validated", true);
    }
}
