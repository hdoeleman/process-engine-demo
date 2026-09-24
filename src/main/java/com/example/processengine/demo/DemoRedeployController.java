package com.example.processengine.demo;

import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.engine.ProcessEngine;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-only convenience: redeploys every bundled demo process the admin console's "Clear all
 *  data" (which wipes every deployed definition, for every process key, with no scoping) just
 *  wiped, without restarting the app -- {@link DemoController}'s own deploy-v1/deploy-v2 endpoints
 *  only ever covered order-fulfillment itself, leaving the other eight bundled processes (and
 *  order-fulfillment v2's own "customer-notification" dependency) with no recovery path short of a
 *  restart. Deliberately not in the admin console for the same reason {@link DemoController} isn't:
 *  it knows about this app's specific bundled processes, which the admin console (process-agnostic)
 *  is not supposed to know about. */
@RestController
@RequestMapping("/api/demo")
public class DemoRedeployController {

    private final ProcessEngine engine;
    private final ProcessDefinitionRepository definitionRepo;

    public DemoRedeployController(ProcessEngine engine, ProcessDefinitionRepository definitionRepo) {
        this.engine = engine;
        this.definitionRepo = definitionRepo;
    }

    /** Redeploys order-fulfillment v1 (matching what {@link DemoDataLoader} deploys at first boot --
     *  an operator who'd loaded v2 before clearing data can still re-load it afterward via {@link
     *  DemoController#deployV2}) plus every other bundled process, each only if it isn't already
     *  PUBLISHED -- so this is also safe to call when nothing was actually wiped. */
    @PostMapping("/redeploy-all")
    public void redeployAll() {
        if (definitionRepo
                .findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, com.example.processengine.definition.DefinitionStatus.PUBLISHED)
                .isEmpty()) {
            engine.deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
        }
        BundledProcessDefinitions.deployAllIfAbsent(engine, definitionRepo);
    }
}
