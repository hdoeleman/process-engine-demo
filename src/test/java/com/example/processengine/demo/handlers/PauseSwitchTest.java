package com.example.processengine.demo.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.engine.StepExecutionContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PauseSwitchTest {

    @Test
    void doesNothingWhenTheSlowSwitchIsNotSet() throws InterruptedException {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "chargePayment", 1, Map.of());

        var start = System.currentTimeMillis();
        PauseSwitch.pauseIfRequested(ctx);

        assertThat(System.currentTimeMillis() - start).isLessThan(200);
    }

    @Test
    void doesNothingWhenTheSlowSwitchIsFalse() throws InterruptedException {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "chargePayment", 1, Map.of("chargePaymentSlow", false));

        var start = System.currentTimeMillis();
        PauseSwitch.pauseIfRequested(ctx);

        assertThat(System.currentTimeMillis() - start).isLessThan(200);
    }

    // Proves a real, non-trivial pause without the test itself waiting out the full 11s -- same
    // technique HandlerTimeoutTest uses: run it on its own thread, confirm it's still blocked well
    // past when a no-op would've returned, then interrupt it rather than waiting it out.
    @Test
    void pausesLongEnoughToBeInterruptedWhenTheSlowSwitchIsSet() throws InterruptedException {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "chargePayment", 1, Map.of("chargePaymentSlow", true));
        AtomicReference<Throwable> caught = new AtomicReference<>();
        var worker = new Thread(() -> {
            try {
                PauseSwitch.pauseIfRequested(ctx);
            } catch (Throwable t) {
                caught.set(t);
            }
        });

        worker.start();
        Thread.sleep(300);
        assertThat(worker.isAlive())
                .as("still paused well short of the full 11s")
                .isTrue();
        worker.interrupt();
        worker.join(1000);

        assertThat(caught.get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    void onlyThatStepsOwnSlowVariableTriggersThePause() throws InterruptedException {
        var ctx = new StepExecutionContext(UUID.randomUUID(), "chargePayment", 1, Map.of("reserveInventorySlow", true));

        var start = System.currentTimeMillis();
        PauseSwitch.pauseIfRequested(ctx);

        assertThat(System.currentTimeMillis() - start).isLessThan(200);
    }
}
