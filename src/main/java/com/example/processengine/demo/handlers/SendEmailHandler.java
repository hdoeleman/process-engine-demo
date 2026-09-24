package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Second and last step of {@code customer-notification} -- sets {@code confirmationEmailSent},
 *  copied back onto the parent order-fulfillment instance once the sub-process completes (every
 *  variable the child ends with comes back, not just this one -- see ADR-022), the same variable
 *  name and meaning {@code SendConfirmationEmailHandler} used to set directly before this became a
 *  sub-process. */
@Component("sendEmail")
public class SendEmailHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendEmailHandler.class);

    private final SimulatedLatency latency;

    public SendEmailHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        log.info(
                "[{}] sent confirmation email for invoice {}",
                ctx.getProcessInstanceId(),
                ctx.getVariable("invoiceId"));
        ctx.setVariable("confirmationEmailSent", true);
    }
}
