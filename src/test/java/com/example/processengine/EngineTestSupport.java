package com.example.processengine;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.awaitility.Awaitility;

final class EngineTestSupport {

    private EngineTestSupport() {}

    /** start -> flaky (SERVICE_TASK) -> end, used to exercise failure/retry/resume in isolation. */
    static ProcessDefinitionSpec flakySingleStepSpec(int maxAttempts) {
        return new ProcessDefinitionSpec(
                "Flaky single step",
                "flakyStep",
                List.of(
                        StepSpecs.serviceTask("flakyStep", "Flaky step", "flaky", maxAttempts, 50L),
                        StepSpecs.end("end", "End")),
                List.of(TransitionSpecs.to("flakyStep", "end")));
    }

    /** start -> hangStep (SERVICE_TASK, "hangs" handler) -> end, used to exercise the AMBIGUOUS
     *  dispatch-outcome-unknown flow (ADR-011): the handler blocks until the test releases it. */
    static ProcessDefinitionSpec hangingSingleStepSpec(int maxAttempts) {
        return new ProcessDefinitionSpec(
                "Hanging single step",
                "hangStep",
                List.of(
                        StepSpecs.serviceTask("hangStep", "Hang step", "hangs", maxAttempts, 50L),
                        StepSpecs.end("end", "End")),
                List.of(TransitionSpecs.to("hangStep", "end")));
    }

    static void awaitStatus(ProcessInstanceRepository repo, UUID instanceId, InstanceStatus expected) {
        awaitStatus(repo, instanceId, status -> status == expected);
    }

    static void awaitStatus(ProcessInstanceRepository repo, UUID instanceId, Predicate<InstanceStatus> predicate) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    var instance = repo.findById(instanceId).orElseThrow();
                    if (!predicate.test(instance.getStatus())) {
                        throw new AssertionError("status was " + instance.getStatus());
                    }
                });
    }
}
