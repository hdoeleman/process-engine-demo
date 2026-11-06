package com.example.processengine.demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Holds every currently-connected browser tab's SSE connection and pushes a warning to all of
 *  them the moment {@link DemoAdvisoryListener} reports one -- real push, no polling on either
 *  side. A tab that closes, errors, or times out is dropped from the list via its own emitter
 *  callback; nothing here has to actively detect a dead connection. */
@Component
public class DemoWarningBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DemoWarningBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // no timeout (0L) -- a demo page is expected to stay open indefinitely, not time out mid-session.
    public SseEmitter subscribe() {
        var emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            // A container may not flush the response headers to the client until the first byte
            // of the body is actually written -- without this, a tab that never sees a warning
            // has no way to know the connection even succeeded (EventSource stays "connecting").
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(DemoWarning warning) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("warning").data(warning));
            } catch (Exception e) {
                // Not completeWithError(e) here -- a send failure almost always means the
                // underlying connection is already gone (a closed tab, a dropped network
                // connection), and the container may have already started its own error handling
                // for it, in which case forcing another completion throws a second, unrelated
                // IllegalStateException ("AsyncContext after an error had occurred"). Just stop
                // tracking it; whichever of onCompletion/onTimeout/onError the container ends up
                // calling will find nothing left to remove.
                log.debug("Dropping a dead SSE subscriber that failed to receive a warning", e);
                emitters.remove(emitter);
            }
        }
    }

    int subscriberCount() {
        return emitters.size();
    }
}
