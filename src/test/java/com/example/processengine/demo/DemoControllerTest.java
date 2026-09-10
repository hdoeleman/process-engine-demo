package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.processengine.definition.ProcessDefinitionEntity;
import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.definition.StepDefinitionEntity;
import com.example.processengine.definition.TransitionEntity;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpec;
import com.example.processengine.dto.TransitionSpec;
import com.example.processengine.engine.ProcessEngine;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

    @Mock
    private ProcessEngine engine;

    @Mock
    private ProcessDefinitionRepository definitionRepo;

    private DemoController controller;

    @BeforeEach
    void setUp() {
        controller = new DemoController(engine, definitionRepo);
    }

    /** Builds an entity whose content round-trips back to exactly {@code spec} via
     *  {@link ProcessDefinitionSpec#of} -- the same field-by-field mapping
     *  {@code ProcessDefinitionService.deploy} itself does, so this is what a real deploy of
     *  {@code spec} would have produced, not a hand-rolled shortcut. */
    private static ProcessDefinitionEntity entityFrom(String key, int version, ProcessDefinitionSpec spec) {
        var entity = new ProcessDefinitionEntity(key, version, spec.name(), spec.startStepKey());
        for (StepSpec s : spec.steps()) {
            entity.addStep(new StepDefinitionEntity(
                    s.key(),
                    s.name(),
                    s.type(),
                    s.handlerRef(),
                    s.maxAttemptsOrDefault(),
                    s.retryBackoffMillisOrDefault()));
        }
        for (TransitionSpec t : spec.transitions()) {
            entity.addTransition(new TransitionEntity(t.from(), t.to(), t.condition(), t.defaultBranchOrDefault()));
        }
        entity.setStartVariables(spec.startVariablesOrEmpty().stream()
                .map(v -> new com.example.processengine.definition.VariableSpec(
                        v.name(), v.type(), v.description(), v.defaultValue()))
                .toList());
        return entity;
    }

    @Test
    void deployV1DeploysTheV1SpecUnderTheDemoProcessKey() {
        var deployed = new ProcessDefinitionEntity(
                DemoProcessDefinitions.PROCESS_KEY, 1, "Order Fulfillment", "validateOrder");
        when(engine.deploy(eq(DemoProcessDefinitions.PROCESS_KEY), eq(DemoProcessDefinitions.v1())))
                .thenReturn(deployed);

        var view = controller.deployV1();

        assertThat(view.version()).isEqualTo(1);
        verify(engine).deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
    }

    @Test
    void deployV2DeploysTheV2SpecUnderTheDemoProcessKey() {
        var deployed = new ProcessDefinitionEntity(
                DemoProcessDefinitions.PROCESS_KEY, 2, "Order Fulfillment", "validateOrder");
        when(engine.deploy(eq(DemoProcessDefinitions.PROCESS_KEY), eq(DemoProcessDefinitions.v2())))
                .thenReturn(deployed);

        var view = controller.deployV2();

        assertThat(view.version()).isEqualTo(2);
        verify(engine).deploy(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v2());
    }

    @Test
    void deploymentStatusReportsNeitherDeployedWhenNothingIsDeployedYet() {
        when(definitionRepo.findByProcessKeyOrderByVersionDesc(DemoProcessDefinitions.PROCESS_KEY))
                .thenReturn(List.of());

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isFalse();
        assertThat(status.v2Deployed()).isFalse();
    }

    @Test
    void deploymentStatusReportsV1DeployedWhenAnExactV1MatchExists() {
        var v1Entity =
                entityFrom(DemoProcessDefinitions.PROCESS_KEY, 1, DemoProcessDefinitions.v1());
        when(definitionRepo.findByProcessKeyOrderByVersionDesc(DemoProcessDefinitions.PROCESS_KEY))
                .thenReturn(List.of(v1Entity));

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isTrue();
        assertThat(status.v2Deployed()).isFalse();
    }

    @Test
    void deploymentStatusReportsBothDeployedWhenBothExactMatchesExist() {
        var v1Entity =
                entityFrom(DemoProcessDefinitions.PROCESS_KEY, 1, DemoProcessDefinitions.v1());
        var v2Entity =
                entityFrom(DemoProcessDefinitions.PROCESS_KEY, 2, DemoProcessDefinitions.v2());
        when(definitionRepo.findByProcessKeyOrderByVersionDesc(DemoProcessDefinitions.PROCESS_KEY))
                .thenReturn(List.of(v2Entity, v1Entity));

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isTrue();
        assertThat(status.v2Deployed()).isTrue();
    }

    @Test
    void deploymentStatusIgnoresAVersionThatDoesNotExactlyMatchEitherTemplate() {
        var unrelatedSpec = new ProcessDefinitionSpec(
                "Something Else",
                "start",
                List.of(new StepSpec("start", "Start", com.example.processengine.definition.StepType.END, null, 1, 0L)),
                List.of());
        var unrelatedEntity = entityFrom(DemoProcessDefinitions.PROCESS_KEY, 1, unrelatedSpec);
        when(definitionRepo.findByProcessKeyOrderByVersionDesc(DemoProcessDefinitions.PROCESS_KEY))
                .thenReturn(List.of(unrelatedEntity));

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isFalse();
        assertThat(status.v2Deployed()).isFalse();
    }
}
