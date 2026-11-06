package com.example.processengine.demo.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimulatedLatencyTest {

    @Test
    void randomDelayMillisStaysWithinTheInclusiveRange() {
        for (int i = 0; i < 200; i++) {
            var delay = SimulatedLatency.randomDelayMillis(10, 20);
            assertThat(delay).isBetween(10L, 20L);
        }
    }

    @Test
    void randomDelayMillisReturnsExactlyThatValueWhenMinEqualsMax() {
        assertThat(SimulatedLatency.randomDelayMillis(5, 5)).isEqualTo(5L);
    }

    // Defensive, not an expected real config -- a fat-fingered min/max swap in application.yml
    // shouldn't crash the app (ThreadLocalRandom.nextLong throws if origin > bound).
    @Test
    void randomDelayMillisToleratesAReversedMinAndMax() {
        for (int i = 0; i < 50; i++) {
            var delay = SimulatedLatency.randomDelayMillis(20, 10);
            assertThat(delay).isBetween(10L, 20L);
        }
    }

    @Test
    void sleepReturnsImmediatelyWhenBothBoundsAreZero() throws InterruptedException {
        var latency = new SimulatedLatency(0, 0);

        var start = System.currentTimeMillis();
        latency.sleep();

        assertThat(System.currentTimeMillis() - start).isLessThan(50);
    }

    // Small range (not the real 1000-3000ms default) so this stays fast -- proves sleep() is a
    // real block, not a no-op, without the test itself paying seconds for it.
    @Test
    void sleepActuallyBlocksForAtLeastTheConfiguredMinimum() throws InterruptedException {
        var latency = new SimulatedLatency(30, 40);

        var start = System.currentTimeMillis();
        latency.sleep();

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(25);
    }
}
