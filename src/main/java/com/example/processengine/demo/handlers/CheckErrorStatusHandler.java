package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Defaults {@code action} when the parent send-pdf instance never set it (e.g. it failed before
 *  ever assigning one) -- {@code actionGateway}'s own condition references it by name, and an
 *  entirely-absent variable throws during SpEL evaluation rather than evaluating as null (see
 *  {@code CheckMsmcRequestIdHandler}'s own Javadoc for why). */
@Component("checkErrorStatus")
public class CheckErrorStatusHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckErrorStatusHandler.class);

    private final SimulatedLatency latency;

    public CheckErrorStatusHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        if (ctx.getVariable("action") == null) {
            ctx.setVariable("action", "none");
        }
        log.info("[{}] checked error status", ctx.getProcessInstanceId());
    }
}
