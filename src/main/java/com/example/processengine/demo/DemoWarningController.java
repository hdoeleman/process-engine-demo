package com.example.processengine.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Backs the demo UI's live warning banner -- a browser tab opens one long-lived connection here
 *  and {@link DemoAdvisoryListener} pushes to it directly the moment an advisory situation
 *  occurs. No polling on either side. */
@RestController
@RequestMapping("/api/demo/warnings")
public class DemoWarningController {

    private final DemoWarningBroadcaster broadcaster;

    public DemoWarningController(DemoWarningBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
