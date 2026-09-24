package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Guarded (routes to "rollback" on the "ROLLBACK_ERROR" code -- see SendPdfProcessDefinitions):
 *  set "sendAgiEmailFail": true + "sendAgiEmailFailErrorCode": "ROLLBACK_ERROR" as start variables
 *  to see the routed rollback path instead of the default full saga compensation. Reached only
 *  after {@code waitForSignature} (ADR-025's TIMER_WAIT step) elapses. */
@Component("sendAgiEmail")
public class SendAgiEmailHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendAgiEmailHandler.class);

    private final SimulatedLatency latency;

    public SendAgiEmailHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] sent AGI email", ctx.getProcessInstanceId());
    }
}
