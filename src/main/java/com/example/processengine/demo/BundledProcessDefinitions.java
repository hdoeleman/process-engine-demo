package com.example.processengine.demo;

import com.example.processengine.definition.DefinitionStatus;
import com.example.processengine.definition.ProcessDefinitionRepository;
import com.example.processengine.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The single list of every bundled demo process besides order-fulfillment itself (which has its
 *  own v1/v2 pair and its own UI-facing deploy-status polling -- see {@link DemoController}).
 *  Shared between {@link DemoDataLoader} (deploys these once, at first boot) and {@link
 *  DemoRedeployController} (redeploys whichever of these "Clear all data" just wiped, without
 *  restarting the app) so the two never drift out of sync -- the list used to exist only inside
 *  {@code DemoDataLoader}, which meant a demo process added there was invisible to data-reset
 *  recovery until someone remembered to wire it up a second time. */
final class BundledProcessDefinitions {

    private static final Logger log = LoggerFactory.getLogger(BundledProcessDefinitions.class);

    private BundledProcessDefinitions() {}

    /** Deploys every entry not already PUBLISHED under its own key -- a no-op for whichever ones
     *  are still deployed, so safe to call both at boot and on demand after a data wipe. Order
     *  matters only for the log line, not correctness: {@code DefinitionValidator} doesn't check
     *  {@code calledProcessKey} existence at deploy time (see the call sites' own comments), so a
     *  SUB_PROCESS parent can be deployed before or after the child it calls. */
    static void deployAllIfAbsent(ProcessEngine engine, ProcessDefinitionRepository definitionRepo) {
        deployIfAbsent(
                engine, definitionRepo,
                CustomerNotificationProcessDefinitions.PROCESS_KEY, CustomerNotificationProcessDefinitions.v1());
        deployIfAbsent(
                engine, definitionRepo,
                ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY,
                ActivateInsuranceRollbackProcessDefinitions.v1());
        deployIfAbsent(
                engine, definitionRepo,
                ActivateInsuranceProcessDefinitions.PROCESS_KEY, ActivateInsuranceProcessDefinitions.v1());
        deployIfAbsent(
                engine, definitionRepo,
                SendPdfRollbackProcessDefinitions.PROCESS_KEY, SendPdfRollbackProcessDefinitions.v1());
        deployIfAbsent(engine, definitionRepo, SendPdfProcessDefinitions.PROCESS_KEY, SendPdfProcessDefinitions.v1());
        deployIfAbsent(
                engine, definitionRepo,
                SupplierQuoteProcessDefinitions.PROCESS_KEY, SupplierQuoteProcessDefinitions.v1());
        deployIfAbsent(
                engine, definitionRepo,
                MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, MultiSupplierQuoteProcessDefinitions.v1());
    }

    private static void deployIfAbsent(
            ProcessEngine engine,
            ProcessDefinitionRepository definitionRepo,
            String processKey,
            com.example.processengine.dto.ProcessDefinitionSpec spec) {
        if (definitionRepo
                .findTopByProcessKeyAndStatusOrderByVersionDesc(processKey, DefinitionStatus.PUBLISHED)
                .isPresent()) {
            return;
        }
        engine.deploy(processKey, spec);
        log.info("Deployed demo definition '{}' v1", processKey);
    }
}
