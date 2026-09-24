package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Deliberately unguarded (no on-failure route, see ActivateInsuranceProcessDefinitions): its
 *  permanent failure demonstrates today's unchanged default -- full saga compensation -- in
 *  contrast with the four guarded steps that route to the shared rollback instead. */
@Component("activateInsuranceAgi")
public class ActivateInsuranceAgiHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ActivateInsuranceAgiHandler.class);

    private final SimulatedLatency latency;

    public ActivateInsuranceAgiHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        log.info("[{}] activated insurance in AGI", ctx.getProcessInstanceId());
    }
}
