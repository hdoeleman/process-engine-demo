package com.example.processengine.demo;

import com.example.processengine.definition.DefinitionStatus;
import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Deploys every bundled demo process definition on first startup only, so restarts don't pile up
 *  new versions. */
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
        deployIfAbsent(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
        // Every other bundled process (customer-notification, activate-insurance(-rollback),
        // send-pdf(-rollback), supplier-quote, multi-supplier-quote -- whose own multi-instance
        // fan-out over "supplier-quote" is covered by ADR-021, the same ADR as every other
        // SUB_PROCESS child) -- shared with DemoRedeployController so a demo process added here is
        // never invisible to data-reset recovery, see BundledProcessDefinitions' own Javadoc.
        BundledProcessDefinitions.deployAllIfAbsent(engine, definitionRepo);
    }

    private void deployIfAbsent(String processKey, ProcessDefinitionSpec spec) {
        if (definitionRepo
                .findTopByProcessKeyAndStatusOrderByVersionDesc(processKey, DefinitionStatus.PUBLISHED)
                .isPresent()) {
            return;
        }
        engine.deploy(processKey, spec);
        log.info("Deployed demo definition '{}' v1", processKey);
    }
}
