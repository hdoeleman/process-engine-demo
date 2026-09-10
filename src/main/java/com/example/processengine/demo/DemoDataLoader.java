package com.example.processengine.demo;

import com.example.processengine.definition.DefinitionStatus;
import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Deploys order-fulfillment v1 on first startup only, so restarts don't pile up new versions. */
@Component
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ProcessEngine engine;
    private final ProcessDefinitionRepository definitionRepo;
    private final boolean enabled;

    public DemoDataLoader(
            ProcessEngine engine,
            ProcessDefinitionRepository definitionRepo,
            @Value("${process-engine.demo.load-sample-definition:true}") boolean enabled) {
        this.engine = engine;
        this.definitionRepo = definitionRepo;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (definitionRepo
                .findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED)
                .isPresent()) {
            return;
        }
        engine.deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
        log.info("Deployed demo definition '{}' v1", DemoProcessDefinitions.PROCESS_KEY);
    }
}
