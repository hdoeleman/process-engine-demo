package com.example.processengine.demo.handlers;

import com.example.processengine.engine.CompensatingStepHandler;
import com.example.processengine.engine.StepExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Compensable: its compensation deactivates the insurance in AGI again. Deliberately unguarded (no on-failure
 *  route, see ActivateInsuranceProcessDefinitions): its permanent failure demonstrates the default, compensating the
 *  completed steps and failing the instance, in contrast with the guarded steps that also follow a route. */
@Component("activateInsuranceAgi")
public class ActivateInsuranceAgiHandler implements CompensatingStepHandler {

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

    @Override
    public void compensate(StepExecutionContext ctx) {
        // May be called again by an operator after a failed compensation, so it is safe to repeat.
        log.info("[{}] deactivated insurance in AGI", ctx.getProcessInstanceId());
    }
}
