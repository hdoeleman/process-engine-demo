package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.processengine.definition.ProcessDefinitionEntity;
import com.example.processengine.engine.ProcessEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

    @Mock
    private ProcessEngine engine;

    private DemoController controller;

    @BeforeEach
    void setUp() {
        controller = new DemoController(engine);
    }

    /** Stubs the facade's exact-content match for v1/v2; a null version means "no such deployed
     *  version" (an unstubbed Integer-returning mock call would answer 0, not null). */
    private void stubMatches(Integer v1Version, Integer v2Version) {
        when(engine.findMatchingVersion(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1()))
                .thenReturn(v1Version);
        when(engine.findMatchingVersion(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v2()))
                .thenReturn(v2Version);
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
    void deploymentStatusReportsNeitherDeployedWhenNeitherSpecMatchesAnyVersion() {
        stubMatches(null, null);

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isFalse();
        assertThat(status.v2Deployed()).isFalse();
    }

    @Test
    void deploymentStatusReportsV1DeployedWhenOnlyV1Matches() {
        stubMatches(1, null);

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isTrue();
        assertThat(status.v2Deployed()).isFalse();
    }

    @Test
    void deploymentStatusReportsBothDeployedWhenBothMatch() {
        stubMatches(1, 2);

        var status = controller.deploymentStatus();

        assertThat(status.v1Deployed()).isTrue();
        assertThat(status.v2Deployed()).isTrue();
    }
}
