package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.EventType;
import com.example.processengine.runtime.ProcessEventEntity;
import com.example.processengine.runtime.ProcessInstanceRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves, with real concurrent threads against a real database (not the mocked-repository
 * fixture most engine unit tests use), the property every design review of this engine has
 * praised but nothing actually exercised: {@code findByIdForUpdate}'s pessimistic lock genuinely
 * serializes concurrent writers to the same instance rather than merely making it look that way
 * in single-threaded tests. Without this, a regression that swapped the locking read for a plain
 * {@code findById} somewhere would compile, pass every other test, and silently reintroduce lost
 * updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class InstanceLockConcurrencyTest {

    private static final int WRITERS = 16;

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    @Test
    void concurrentEditVariablesCallsOnTheSameInstanceLoseNoUpdates() throws InterruptedException {
        engine.deploy("lock-race", DemoProcessDefinitions.v1());
        var instance = engine.start("lock-race", Map.of("orderId", "ORD-LOCK-1"));
        var instanceId = instance.getId();
        engine.pause(instanceId); // editVariables is only legal while PAUSED (ADR-012)

        runConcurrently(instanceId);

        // Each writer only ever touches its own key -- a lost update shows up as a missing key
        // here, not as a wrong value on a shared one.
        var variables = instanceRepo.findById(instanceId).orElseThrow().getVariables();
        for (int i = 0; i < WRITERS; i++) {
            assertThat(variables).as("key k%d", i).containsEntry("k" + i, i);
        }
    }

    @Test
    void concurrentEditVariablesCallsProduceDistinctEventSequenceNumbers() throws InterruptedException {
        // Same race, checked a second, independent way: instance.nextSeq() is only ever
        // incremented while the instance row is locked, and process_events has a DB-level
        // UNIQUE(process_instance_id, seq) constraint -- a genuine locking failure would either
        // collide two events on the same seq (caught here) or throw a constraint violation
        // (caught as a test failure before this assertion ever runs).
        engine.deploy("lock-race-seq", DemoProcessDefinitions.v1());
        var instance = engine.start("lock-race-seq", Map.of("orderId", "ORD-LOCK-2"));
        var instanceId = instance.getId();
        engine.pause(instanceId);

        runConcurrently(instanceId);

        var seqs = engine.eventsOf(instanceId).stream()
                .filter(e -> e.getType() == EventType.VARIABLES_EDITED)
                .map(ProcessEventEntity::getSeq)
                .toList();
        assertThat(seqs).hasSize(WRITERS);
        assertThat(seqs).doesNotHaveDuplicates();
    }

    /** Fires {@code WRITERS} concurrent {@code editVariables} calls at {@code instanceId}, each
     *  adding its own distinct key, gated by a latch so they genuinely overlap rather than just
     *  happening to interleave. Fails immediately if any writer threw. */
    private void runConcurrently(UUID instanceId) throws InterruptedException {
        var pool = Executors.newFixedThreadPool(WRITERS);
        var ready = new CountDownLatch(WRITERS);
        var go = new CountDownLatch(1);
        CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < WRITERS; i++) {
                var key = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(10, TimeUnit.SECONDS);
                        engine.editVariables(instanceId, Map.of("k" + key, key));
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("every writer reached the start line")
                    .isTrue();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS))
                    .as("every writer finished")
                    .isTrue();
        }
        assertThat(failures).isEmpty();
    }
}
