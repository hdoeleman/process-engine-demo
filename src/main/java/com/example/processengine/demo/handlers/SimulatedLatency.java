package com.example.processengine.demo.handlers;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Every bundled demo handler pauses for a random duration (default 5-8s) before doing its work,
 *  via this one shared bean, so a demo run feels like it's actually calling something instead of
 *  completing instantly -- makes the admin console's RUNNING status, auto-refresh polling, and
 *  diagram animation actually visible instead of blinking past in milliseconds. The range is
 *  configurable specifically so the test profile can set it to 0 (see application-test.yml)
 *  rather than paying this delay on every one of the suite's many handler invocations. Unlike
 *  {@link FailSwitch}/{@link PauseSwitch} (opt-in per instance, via a start variable), this is a
 *  deployment-environment concern -- real app vs. test run -- which is what Spring config/profiles
 *  are for, so it's a real bean rather than a static utility reading a per-instance variable. */
@Component
public class SimulatedLatency {

    private final long minMillis;
    private final long maxMillis;

    public SimulatedLatency(
            @Value("${process-engine.demo.simulated-latency-min-millis:5000}") long minMillis,
            @Value("${process-engine.demo.simulated-latency-max-millis:8000}") long maxMillis) {
        this.minMillis = minMillis;
        this.maxMillis = maxMillis;
    }

    public void sleep() throws InterruptedException {
        var delay = randomDelayMillis(minMillis, maxMillis);
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    static long randomDelayMillis(long min, long max) {
        var lo = Math.min(min, max);
        var hi = Math.max(min, max);
        return ThreadLocalRandom.current().nextLong(lo, hi + 1);
    }
}
