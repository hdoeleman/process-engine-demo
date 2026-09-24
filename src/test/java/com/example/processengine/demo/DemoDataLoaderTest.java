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
        // run() deploys every sample process key unconditionally -- also stubbing the rest as
        // already-published so this test can still assert nothing gets (re)deployed at all.
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        CustomerNotificationProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(CustomerNotificationProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(new ProcessDefinitionEntity(
                        ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        ActivateInsuranceProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(ActivateInsuranceProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SendPdfRollbackProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(SendPdfRollbackProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SendPdfProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(
                        Optional.of(new ProcessDefinitionEntity(SendPdfProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SupplierQuoteProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(SupplierQuoteProcessDefinitions.PROCESS_KEY, 1, "n", "s")));
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, 1, "n", "s")));

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine, never()).deploy(any(), any());
    }

    @Test
    void deploysSendPdfWhenNoPublishedVersionExistsYet() {
        // run() deploys every sample process key unconditionally, in a fixed order -- Mockito's
        // strict stubbing throws PotentialStubbingProblem on an unstubbed call to a method that
        // still has an as-yet-unmatched stub pending elsewhere, so every key called *before* the
        // one this test actually cares about must be stubbed too (order-fulfillment through
        // activate-insurance and send-pdf-rollback all precede send-pdf itself), same reasoning
        // skipsDeployingWhenAPublishedVersionAlreadyExists above already applies.
        alreadyPublished(DemoProcessDefinitions.PROCESS_KEY);
        alreadyPublished(CustomerNotificationProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SendPdfRollbackProcessDefinitions.PROCESS_KEY);
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SendPdfProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(SendPdfProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void deploysSendPdfRollbackWhenNoPublishedVersionExistsYet() {
        alreadyPublished(DemoProcessDefinitions.PROCESS_KEY);
        alreadyPublished(CustomerNotificationProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceProcessDefinitions.PROCESS_KEY);
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SendPdfRollbackProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(SendPdfRollbackProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void deploysSupplierQuoteWhenNoPublishedVersionExistsYet() {
        alreadyPublished(DemoProcessDefinitions.PROCESS_KEY);
        alreadyPublished(CustomerNotificationProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SendPdfRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SendPdfProcessDefinitions.PROCESS_KEY);
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        SupplierQuoteProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(SupplierQuoteProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void deploysMultiSupplierQuoteWhenNoPublishedVersionExistsYet() {
        alreadyPublished(DemoProcessDefinitions.PROCESS_KEY);
        alreadyPublished(CustomerNotificationProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(ActivateInsuranceProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SendPdfRollbackProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SendPdfProcessDefinitions.PROCESS_KEY);
        alreadyPublished(SupplierQuoteProcessDefinitions.PROCESS_KEY);
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(MultiSupplierQuoteProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    private void alreadyPublished(String processKey) {
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(processKey, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(new ProcessDefinitionEntity(processKey, 1, "n", "s")));
    }

    @Test
    void deploysCustomerNotificationWhenNoPublishedVersionExistsYet() {
        // run() also deploys order-fulfillment -- stubbed here too (strict stubs otherwise flag
        // that unstubbed invocation as a potential mismatch, not just let it fall through).
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        CustomerNotificationProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine).deploy(eq(CustomerNotificationProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void skipsDeployingCustomerNotificationWhenAPublishedVersionAlreadyExists() {
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        DemoProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(definitionRepo.findTopByProcessKeyAndStatusOrderByVersionDesc(
                        CustomerNotificationProcessDefinitions.PROCESS_KEY, DefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(
                        new ProcessDefinitionEntity(CustomerNotificationProcessDefinitions.PROCESS_KEY, 1, "n", "s")));

        new DemoDataLoader(engine, definitionRepo, true).run(args);

        verify(engine, never()).deploy(eq(CustomerNotificationProcessDefinitions.PROCESS_KEY), any());
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
