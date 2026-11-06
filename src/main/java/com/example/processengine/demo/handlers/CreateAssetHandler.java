package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Compensable: its compensation removes the asset from DBBIEN. Guarded (routes to "rollback" on failure, see
 *  ActivateInsuranceProcessDefinitions). */
@Component("createAsset")
public class CreateAssetHandler implements CompensatingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateAssetHandler.class);

    private final SimulatedLatency latency;

    public CreateAssetHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] created asset in DBBIEN", ctx.getProcessInstanceId());
    }

    @Override
    public void compensate(StepExecutionContext ctx) {
        // May be called again by an operator after a failed compensation, so it is safe to repeat.
        log.info("[{}] removed asset from DBBIEN", ctx.getProcessInstanceId());
    }
}
