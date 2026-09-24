package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** First step of {@code multi-supplier-quote}. Also where {@code suppliers} -- the collection
 *  {@code requestQuotes}'s multi-instance fan-out (ADR-027) reads right after this step -- gets
 *  turned from a comma-separated string into a real {@code List}: {@code VariableType} has no
 *  list-valued widget (only STRING/BOOLEAN/NUMBER), so the Business User Console's "New instance"
 *  form can only offer {@code suppliers} as a plain text field (pre-filled via its {@code
 *  VariableSpec} default) rather than the JSON-fallback textarea this process's own declared
 *  Fail/Slow switches already rule out (that fallback only appears when a version declares zero
 *  start variables at all). Blank or unset defaults to zero suppliers -- same "none behavior"
 *  {@code requestQuotes} itself falls back to for an empty/missing collection. */
@Component("prepareRequest")
public class PrepareRequestHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(PrepareRequestHandler.class);

    private final SimulatedLatency latency;

    public PrepareRequestHandler(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        latency.sleep();
        FailSwitch.throwIfRequested(ctx);
        PauseSwitch.pauseIfRequested(ctx);
        var raw = ctx.getVariable("suppliers");
        if (raw instanceof String s) {
            List<String> suppliers = Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(n -> !n.isEmpty())
                    .toList();
            ctx.setVariable("suppliers", suppliers);
        }
        log.info("[{}] prepared multi-supplier quote request", ctx.getProcessInstanceId());
    }
}
