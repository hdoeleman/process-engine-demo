package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Test-only handler whose failure state is external to the engine (a static counter keyed by
 * instance id), not derived from the step instance's attempt number. This lets tests exercise
 * "resume after the underlying problem is fixed" (attempt count resets, but the call count that
 * decides success keeps advancing) distinctly from "engine retries until attempts are exhausted".
 */
@Component("flaky")
public class FlakyHandler implements StepHandler {

    private static final Map<UUID, AtomicInteger> CALL_COUNTS = new ConcurrentHashMap<>();

    @Override
    public void execute(StepExecutionContext ctx) {
        var failUntilCount = asInt(ctx.getVariable("flakyFailUntilCount"), 0);
        var callNumber = CALL_COUNTS
                .computeIfAbsent(ctx.getProcessInstanceId(), id -> new AtomicInteger(0))
                .incrementAndGet();
        if (callNumber <= failUntilCount) {
            throw new RuntimeException("flaky failure on call #" + callNumber);
        }
        ctx.setVariable("flakyDone", true);
        ctx.setVariable("flakyCallNumber", callNumber);
    }

    private int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
