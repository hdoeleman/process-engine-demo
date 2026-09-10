package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Only exists in v2 of the order-fulfillment definition -- used to demonstrate versioning. */
@Component("sendConfirmationEmail")
public class SendConfirmationEmailHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendConfirmationEmailHandler.class);

    private final SimulatedLatency latency;

    public SendConfirmationEmailHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info(
                "[{}] sent confirmation email for invoice {}",
                ctx.getProcessInstanceId(),
                ctx.getVariable("invoiceId"));
        ctx.setVariable("confirmationEmailSent", true);
    }
}
