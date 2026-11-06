package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Compensable and idempotent: it sets the status to a fixed value, so a repeat changes nothing, and its
 *  compensation sets the status back. Guarded (routes to "rollback" on failure, see
 *  ActivateInsuranceProcessDefinitions). */
@Component("updateCollateralStatus2")
public class UpdateCollateralStatus2Handler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateCollateralStatus2Handler.class);

    private final SimulatedLatency latency;

    public UpdateCollateralStatus2Handler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] updated collateral status (2)", ctx.getProcessInstanceId());
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        // May be called again by an operator after a failed compensation, so it is safe to repeat.
        log.info("[{}] restored collateral status (2)", ctx.getProcessInstanceId());
    }
}
