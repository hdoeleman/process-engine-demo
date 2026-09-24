package com.example.processengine.demo;

import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.dto.ProcessDefinitionView;
import com.example.processengine.engine.ProcessEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-only convenience: (re-)deploys the bundled order-fulfillment spec versions without the
 *  operator hand-typing their deploy JSON -- backs the demo UI's "Load v1"/"Load v2" buttons.
 *  {@code deployV1} exists specifically so a version can be put back in place after "Clear all
 *  data" wipes every deployed definition, without restarting the app ({@link DemoDataLoader} only
 *  auto-deploys v1 on first boot, when nothing is deployed yet). Deliberately not in the admin
 *  console: this endpoint knows about one specific bundled process, which is exactly what the
 *  admin console (process-agnostic) is not supposed to know about. */
@RestController
@RequestMapping("/api/demo/order-fulfillment")
public class DemoController {

    private final ProcessEngine engine;
    private final ProcessDefinitionRepository definitionRepo;

    public DemoController(ProcessEngine engine, ProcessDefinitionRepository definitionRepo) {
        this.engine = engine;
        this.definitionRepo = definitionRepo;
    }

    @PostMapping("/deploy-v1")
    public ProcessDefinitionView deployV1() {
        return ProcessDefinitionView.of(engine.deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1()));
    }

    @PostMapping("/deploy-v2")
    public ProcessDefinitionView deployV2() {
        return ProcessDefinitionView.of(engine.deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v2()));
    }

    /** Whether a version with exactly v1's/v2's content already exists under this key -- backs
     *  enabling/disabling the "Load v1"/"Load v2" buttons (no point re-deploying an identical
     *  version). Exact content match via {@link com.example.processengine.dto.ProcessDefinitionSpec#matchAmong},
     *  not a heuristic (step count, name, ...) -- so it stays correct regardless of how many
     *  versions are deployed under this key. */
    @GetMapping("/deployment-status")
    public DeploymentStatus deploymentStatus() {
        var versions = definitionRepo.findByProcessKeyOrderByVersionDesc(DemoProcessDefinitions.PROCESS_KEY);
        var v1Deployed = DemoProcessDefinitions.v1().matchAmong(versions).isPresent();
        var v2Deployed = DemoProcessDefinitions.v2().matchAmong(versions).isPresent();
        return new DeploymentStatus(v1Deployed, v2Deployed);
    }

    public record DeploymentStatus(boolean v1Deployed, boolean v2Deployed) {}
}
