package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.springframework.stereotype.Component;

/** First step of {@code customer-notification}, the sub-process order-fulfillment v2's own {@code
 *  sendConfirmationEmail} step delegates to -- see that step's own comment in
 *  DemoProcessDefinitions for why this lives in a separate process instead of just being a step
 *  here. */
@Component("prepareEmail")
public class PrepareEmailHandler implements StepHandler {

    private final SimulatedLatency latency;

    public PrepareEmailHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        ctx.setVariable("emailPrepared", true);
    }
}
