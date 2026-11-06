package com.example.processengine;

import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;
import org.awaitility.Awaitility;

final class EngineTestSupport {

    private EngineTestSupport() {}

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
