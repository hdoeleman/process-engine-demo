package com.example.processengine.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.processengine.definition.DefinitionStatus;
import com.example.processengine.definition.ProcessDefinitionEntity;
import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDataLoaderTest {

    @Mock
    private ProcessEngine engine;

    @Mock
    private ProcessDefinitionRepository definitionRepo;

    @Mock
    private ApplicationArguments args;

    @Test
    void deploysV1WhenNoPublishedVersionExistsYet() {
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(DemoProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void skipsDeployingWhenAPublishedVersionAlreadyExists() {
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(new ProcessDefinitionEntity(DemoProcessDefinitions.PROCESS_KEY, 1, "n", "s")));

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine, never()).deploy(any(), any());
    }

    @Test
    void deploysEvenWhenTheOnlyExistingRowForTheKeyIsALingeringDraft() {
        // A draft never sitting there suppresses the sample from being seeded -- only PUBLISHED
        // counts as "already deployed."
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(DemoProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void doesNothingWhenDisabled() {
        new DemoDataLoader(engine, definitionRepo, false).run(args);

        verify(engine, never()).deploy(any(), any());
    }
}
