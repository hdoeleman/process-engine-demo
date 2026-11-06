package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Defaults {@code type}/{@code isLeasing}/{@code insuranceDemandId} when a caller starts the
 *  process without setting them -- not optional polish: {@code typeGateway}/{@code
 *  leasingGateway}/{@code insuranceDemandGateway}'s own SpEL conditions reference these by name,
 *  and process-engine's {@code MapAccessor}-backed evaluation throws ({@code EL1008E: Property or
 *  field 'type' cannot be found}) on a variable that's entirely absent from the map -- a stricter
 *  failure mode than "missing means null", the same reason flowable-demo's own
 *  GetInsuranceStatusDelegate has to default {@code fullySigned} defensively. A key present with
 *  an explicit {@code null} value evaluates fine; a key never set at all does not. */
@Component("checkMsmcRequestId")
public class CheckMsmcRequestIdHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(CheckMsmcRequestIdHandler.class);

    private final SimulatedLatency latency;

    public CheckMsmcRequestIdHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        if (ctx.getVariable("type") == null) {
            ctx.setVariable("type", "credit");
        }
        if (ctx.getVariable("isLeasing") == null) {
            ctx.setVariable("isLeasing", false);
        }
        if (ctx.getVariable("insuranceDemandId") == null) {
            ctx.setVariable("insuranceDemandId", "DEMAND-1");
        }
        log.info("[{}] checked msmc request id", ctx.getProcessInstanceId());
    }
}
