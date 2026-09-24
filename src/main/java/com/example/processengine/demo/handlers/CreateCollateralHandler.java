package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Guarded (routes to "rollback" on failure -- see ActivateInsuranceProcessDefinitions): set
 *  "createCollateralFail": true as a start variable to see the routed rollback path instead of
 *  today's default full saga compensation. */
@Component("createCollateral")
public class CreateCollateralHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateCollateralHandler.class);

    private final SimulatedLatency latency;

    public CreateCollateralHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] created collateral in DBSUR", ctx.getProcessInstanceId());
    }
}
