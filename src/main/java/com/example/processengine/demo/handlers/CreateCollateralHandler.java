package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Compensable: its compensation removes the collateral from DBSUR. Guarded (routes to "rollback" on failure,
 *  see ActivateInsuranceProcessDefinitions): set "createCollateralFail": true as a start variable to see the
 *  default, and also "createCollateralFailErrorCode": "ROLLBACK_ERROR" to see the completed steps compensated
 *  and then the route followed. */
@Component("createCollateral")
public class CreateCollateralHandler implements CompensatingStepHandler {

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

    @Override
    public void compensate(StepExecutionContext ctx) {
        // May be called again by an operator after a failed compensation, so it is safe to repeat.
        log.info("[{}] removed collateral from DBSUR", ctx.getProcessInstanceId());
    }
}
