package com.example.processengine.demo.handlers;

import com.example.processengine.engine.StepExecutionContext;
import com.example.processengine.engine.StepHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Test-only handler that blocks on a test-controlled latch, standing in for a dispatched call
 * whose real-world effect and outcome are both unknown to the engine (crashed mid-call, or a
 * recovery racing a call still actually executing) -- the scenario ADR-011's AMBIGUOUS status
 * exists for. {@link #arm()} must be called before starting an instance that uses it; the latch
 * is shared (not keyed per instance) since only one such instance runs per test.
 *
 * <p>AWAIT_SECONDS is a safety bound, not a normal code path -- every test releases its latch in
 * a {@code finally} well under a second in. It was previously 10s, which one of
 * AmbiguousStepResolutionTest's own stray background callers (a superseded dispatch's handler
 * thread, still running after the test that started it already released its own latch and
 * moved on -- {@link #LATCH} is a single shared reference, so a call delayed past the next
 * test's {@link #arm()} waits on that test's latch instead of its own) was found to be
 * genuinely hitting, accounting for most of that test class's wall time. 2s keeps the same
 * safety-net behavior at a small fraction of the cost. */
@Component("hangs")
public class HangingHandler implements StepHandler {

    private static final int AWAIT_SECONDS = 2;
    private static final AtomicReference<CountDownLatch> LATCH = new AtomicReference<>(new CountDownLatch(0));

    public static CountDownLatch arm() {
        var latch = new CountDownLatch(1);
        LATCH.set(latch);
        return latch;
    }

    @Override
    public void execute(StepExecutionContext ctx) throws InterruptedException {
        LATCH.get().await(AWAIT_SECONDS, TimeUnit.SECONDS);
    }
}
