package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Guarded (routes to "rollback" on failure -- see ActivateInsuranceProcessDefinitions). */
@Component("updateCollateralStatus1")
public class UpdateCollateralStatus1Handler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateCollateralStatus1Handler.class);

    private final SimulatedLatency latency;

    public UpdateCollateralStatus1Handler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] updated collateral status (1)", ctx.getProcessInstanceId());
    }
}
