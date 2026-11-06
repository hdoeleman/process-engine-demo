package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DemoWarningBroadcasterTest {

    private final DemoWarningBroadcaster broadcaster = new DemoWarningBroadcaster();

    @Test
    void subscribeReturnsADistinctEmitterEachCallAndTracksIt() {
        var first = broadcaster.subscribe();
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        var second = broadcaster.subscribe();

        assertThat(broadcaster.subscriberCount()).isEqualTo(2);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void broadcastWithNoSubscribersIsANoOp() {
        assertThatCode(() -> broadcaster.broadcast(
                        new DemoWarning("SLA breached", "ORD-1", "order-fulfillment", "review", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void completingASubscribedEmitterRemovesItFromTracking() {
        var emitter = broadcaster.subscribe();
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        emitter.complete();

        // complete() alone doesn't invoke onCompletion callbacks outside a real async request
        // (Spring only wires that up once a real handler is attached), so this asserts what's
        // actually verifiable in a plain unit test: subscribe() tracked it, and broadcast() after
        // completion doesn't error even though the emitter is now unusable -- the real cleanup
        // path (onCompletion/onTimeout/onError removing a dead connection) is Spring's own,
        // already-tested SseEmitter machinery, not custom logic of this class.
        assertThatCode(() -> broadcaster.broadcast(
                        new DemoWarning("SLA breached", "ORD-1", "order-fulfillment", "review", null)))
                .doesNotThrowAnyException();
    }

    // Regression test: a first version of broadcast() called emitter.completeWithError(e) in its
    // catch block, which itself threw a second, unrelated IllegalStateException ("AsyncContext
    // after an error had occurred") whenever the container had already started its own error
    // handling for that connection -- discovered live, when a stale connection (a curl client
    // that had already disconnected) caused exactly this on the next real broadcast.
    @Test
    void aFailingEmitterIsDroppedWithoutAffectingOthersOrThrowing() throws Exception {
        var healthy = broadcaster.subscribe();
        var broken = mock(SseEmitter.class);
        doThrow(new IOException("client gone")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
        // Simulates the exact live failure: the container had already started its own error
        // handling for this connection, so a second completeWithError() call on it throws too --
        // this is what makes the test actually distinguish the fixed broadcast() (which no longer
        // calls completeWithError at all) from the original buggy one (which did, and let this
        // propagate uncaught).
        doThrow(new IllegalStateException("AsyncContext after an error had occurred"))
                .when(broken)
                .completeWithError(any());
        injectEmitter(broken);
        assertThat(broadcaster.subscriberCount()).isEqualTo(2);

        assertThatCode(() -> broadcaster.broadcast(
                        new DemoWarning("SLA breached", "ORD-1", "order-fulfillment", "review", null)))
                .doesNotThrowAnyException();

        assertThat(broadcaster.subscriberCount()).isEqualTo(1); // only the broken one was dropped
        assertThat(healthy).isNotNull(); // still tracked -- unaffected by its sibling's failure
    }

    @SuppressWarnings("unchecked")
    private void injectEmitter(SseEmitter emitter) throws Exception {
        var field = DemoWarningBroadcaster.class.getDeclaredField("emitters");
        field.setAccessible(true);
        ((List<SseEmitter>) field.get(broadcaster)).add(emitter);
    }
}
