package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import com.example.processengine.runtime.StepInstanceStatus;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class VersioningTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void instancesStartedBeforeANewVersionDeploysStayPinnedToTheirOriginalVersion() {
        var key = "order-versioning";
        engine.deploy(key, DemoProcessDefinitions.v1());

        var v1Instance = engine.start(key, Map.of("orderId", "ORD-V1"));
        completeReview(v1Instance.getId());

        var v2 = engine.deploy(key, DemoProcessDefinitions.v2());
        assertThat(v2.getVersion()).isEqualTo(2);

        var v2Instance = engine.start(key, Map.of("orderId", "ORD-V2"));
        completeReview(v2Instance.getId());

        EngineTestSupport.awaitStatus(instanceRepo, v1Instance.getId(), InstanceStatus.COMPLETED);
        EngineTestSupport.awaitStatus(instanceRepo, v2Instance.getId(), InstanceStatus.COMPLETED);

        assertThat(v1Instance.getProcessDefinition().getVersion()).isEqualTo(1);
        assertThat(instanceRepo.findById(v1Instance.getId()).orElseThrow().getVariables())
                .doesNotContainKey("confirmationEmailSent");

        assertThat(instanceRepo
                        .findById(v2Instance.getId())
                        .orElseThrow()
                        .getProcessDefinition()
                        .getVersion())
                .isEqualTo(2);
        assertThat(instanceRepo.findById(v2Instance.getId()).orElseThrow().getVariables())
                .containsEntry("confirmationEmailSent", true);
    }

    private void completeReview(UUID instanceId) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var found = engine.stepsOf(instanceId).stream()
                    .anyMatch(s -> s.getStepKey().equals("reviewOrder") && s.getStatus() == StepInstanceStatus.ACTIVE);
            assertThat(found).isTrue();
        });
        var reviewTask = engine.stepsOf(instanceId).stream()
                .filter(s -> s.getStepKey().equals("reviewOrder") && s.getStatus() == StepInstanceStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
        engine.completeStep(instanceId, reviewTask.getId(), Map.of());
    }
}
