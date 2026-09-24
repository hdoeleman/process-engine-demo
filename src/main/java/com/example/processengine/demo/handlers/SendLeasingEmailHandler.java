package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Guarded (routes to "rollback" on the "ROLLBACK_ERROR" code -- see SendPdfProcessDefinitions):
 *  set "sendLeasingEmailFail": true + "sendLeasingEmailFailErrorCode": "ROLLBACK_ERROR" as start
 *  variables to see the routed rollback path instead of the default full saga compensation. */
@Component("sendLeasingEmail")
public class SendLeasingEmailHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(SendLeasingEmailHandler.class);

    private final SimulatedLatency latency;

    public SendLeasingEmailHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] sent leasing email", ctx.getProcessInstanceId());
    }
}
