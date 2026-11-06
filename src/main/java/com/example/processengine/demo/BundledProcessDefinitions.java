package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The single list of every bundled demo process: order-fulfillment v1 (its v2 is only ever loaded
 *  on demand, see {@link DemoController}) plus every other sample process. Shared between {@link
 *  DemoDataLoader} (deploys them once, at first boot) and {@link DemoRedeployController}
 *  (redeploys whichever of them "Clear all data" just wiped, without restarting the app) so the two
 *  never drift out of sync -- the list used to exist only inside {@code DemoDataLoader}, which meant
 *  a demo process added there was invisible to data-reset recovery until someone remembered to wire
 *  it up a second time. Talks to the engine only through the {@link ProcessEngine} facade, never a
 *  repository (enforced by {@code ArchitectureTest}). */
final class BundledProcessDefinitions {

    private static final Logger log = LoggerFactory.getLogger(BundledProcessDefinitions.class);

    private BundledProcessDefinitions() {}

    /** Deploys every entry not already PUBLISHED under its own key -- a no-op for whichever ones
     *  are still deployed, so safe to call both at boot and on demand after a data wipe. Order
     *  matters only for the log line, not correctness: {@code DefinitionValidator} doesn't check
     *  {@code calledProcessKey} existence at deploy time, so a SUB_PROCESS parent can be deployed
     *  before or after the child it calls. */
    static void deployAllIfAbsent(ProcessEngine engine) {
        deployIfAbsent(engine, DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
        deployIfAbsent(
                engine,
                CustomerNotificationProcessDefinitions.PROCESS_KEY,
                CustomerNotificationProcessDefinitions.v1());
        deployIfAbsent(
                engine,
                ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY,
                ActivateInsuranceRollbackProcessDefinitions.v1());
        deployIfAbsent(
                engine, ActivateInsuranceProcessDefinitions.PROCESS_KEY, ActivateInsuranceProcessDefinitions.v1());
        deployIfAbsent(engine, SendPdfRollbackProcessDefinitions.PROCESS_KEY, SendPdfRollbackProcessDefinitions.v1());
        deployIfAbsent(engine, SendPdfProcessDefinitions.PROCESS_KEY, SendPdfProcessDefinitions.v1());
        deployIfAbsent(engine, SupplierQuoteProcessDefinitions.PROCESS_KEY, SupplierQuoteProcessDefinitions.v1());
        deployIfAbsent(
                engine, MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, MultiSupplierQuoteProcessDefinitions.v1());
    }

    /** "Already deployed" means a PUBLISHED version exists -- a lingering DRAFT never suppresses
     *  seeding (the facade's {@code listPublishedVersions} excludes drafts). */
    private static void deployIfAbsent(ProcessEngine engine, String processKey, ProcessDefinitionSpec spec) {
        if (!engine.listPublishedVersions(processKey).isEmpty()) {
            return;
        }
        engine.deploy(processKey, spec);
        log.info("Deployed demo definition '{}' v1", processKey);
    }
}
