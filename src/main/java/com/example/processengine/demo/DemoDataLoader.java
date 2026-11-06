package com.example.processengine.demo;

import com.example.processengine.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Deploys every bundled demo process definition on first startup only, so restarts don't pile up
 *  new versions. The list itself lives in {@link BundledProcessDefinitions}, shared with {@link
 *  DemoRedeployController} so a demo process added there is never invisible to data-reset
 *  recovery. */
@Component
public class DemoDataLoader implements ApplicationRunner {

    private final ProcessEngine engine;
    private final boolean enabled;

    public DemoDataLoader(
            ProcessEngine engine, @Value("${process-engine.demo.load-sample-definition:true}") boolean enabled) {
        this.engine = engine;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        BundledProcessDefinitions.deployAllIfAbsent(engine);
    }
}
