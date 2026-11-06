package com.example.processengine.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.processengine.definition.ProcessDefinitionEntity;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDataLoaderTest {

    /** Every key the loader seeds, i.e. every bundled process. */
    private static final List<String> ALL_KEYS = List.of(
            DemoProcessDefinitions.PROCESS_KEY,
            CustomerNotificationProcessDefinitions.PROCESS_KEY,
            ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY,
            ActivateInsuranceProcessDefinitions.PROCESS_KEY,
            SendPdfRollbackProcessDefinitions.PROCESS_KEY,
            SendPdfProcessDefinitions.PROCESS_KEY,
            SupplierQuoteProcessDefinitions.PROCESS_KEY,
            MultiSupplierQuoteProcessDefinitions.PROCESS_KEY);

    @Mock
    private ProcessEngine engine;

    @Mock
    private ApplicationArguments args;

    static Stream<String> allKeys() {
        return ALL_KEYS.stream();
    }

    /** Stubs every key as already PUBLISHED except {@code missingKey}, which has no published
     *  version. Every key is stubbed (not just the one under test) because the loader asks about
     *  all of them, and strict stubs flag an unstubbed call on a method that has other stubs. */
    private void publishedEverythingExcept(String missingKey) {
        for (String key : ALL_KEYS) {
            when(engine.listPublishedVersions(key))
                    .thenReturn(
                            key.equals(missingKey)
                                    ? List.of()
                                    : List.of(new ProcessDefinitionEntity(key, 1, "n", "s")));
        }
    }

    @Test
    void deploysEveryBundledProcessWhenNothingIsDeployedYet() {
        new DemoDataLoader(engine, true).run(args);

        for (String key : ALL_KEYS) {
            verify(engine).deploy(eq(key), any(ProcessDefinitionSpec.class));
        }
    }

    @Test
    void deploysNothingWhenEveryBundledProcessAlreadyHasAPublishedVersion() {
        publishedEverythingExcept(null);

        new DemoDataLoader(engine, true).run(args);

        verify(engine, never()).deploy(any(), any());
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void deploysOnlyTheProcessThatHasNoPublishedVersion(String missingKey) {
        publishedEverythingExcept(missingKey);

        new DemoDataLoader(engine, true).run(args);

        verify(engine).deploy(eq(missingKey), any(ProcessDefinitionSpec.class));
        for (String key : ALL_KEYS) {
            if (!key.equals(missingKey)) {
                verify(engine, never()).deploy(eq(key), any());
            }
        }
    }

    @Test
    void doesNothingWhenDisabled() {
        new DemoDataLoader(engine, false).run(args);

        verify(engine, never()).deploy(any(), any());
    }
}
