package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@code fullySigned} drives {@code fullySignedGateway} directly -- this handler doesn't compute
 *  it, same as {@code ActivateInsuranceProcessDefinitions}'s own {@code fullySigned} variable.
 *  Defaults it to {@code true} when a caller starts the process without setting it -- {@code
 *  fullySignedGateway}'s condition references it by name, and an entirely-absent variable throws
 *  during SpEL evaluation rather than evaluating as null (see CheckMsmcRequestIdHandler's own
 *  Javadoc for why). */
@Component("checkFullySigned")
public class CheckFullySignedHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckFullySignedHandler.class);

    private final SimulatedLatency latency;

    public CheckFullySignedHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        if (ctx.getVariable("fullySigned") == null) {
            ctx.setVariable("fullySigned", true);
        }
        log.info("[{}] checked fully signed", ctx.getProcessInstanceId());
    }
}
