package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.SendPdfProcessDefinitions;
import com.example.processengine.demo.SendPdfRollbackProcessDefinitions;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.runtime.InstanceStatus;
import com.example.processengine.runtime.ProcessInstanceRepository;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** End-to-end coverage of the bundled send-pdf process -- ADR-025's new TIMER_WAIT step actually
 *  pausing and auto-resuming through the real Spring context/scheduler, the two boundary-error-
 *  guarded steps routing to the shared "rollback" callActivity on a matching ROLLBACK_ERROR code,
 *  and a plain (non-matching) technical failure sailing past that route instead. Mirrors
 *  flowable-demo's own ActivateInsuranceFlowTest in shape (same reasoning: driven through the
 *  engine's real API, not the dispatcher directly) -- the first such flow-level test for a
 *  process-engine-demo bundled process (see VersioningTest for the closest existing precedent). */
@SpringBootTest
@ActiveProfiles("test")
class SendPdfFlowTest {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private ProcessInstanceRepository instanceRepo;

    private void deploySendPdf() {
        engine.deploy(SendPdfRollbackProcessDefinitions.PROCESS_KEY, SendPdfRollbackProcessDefinitions.v1());
        engine.deploy(SendPdfProcessDefinitions.PROCESS_KEY, SendPdfProcessDefinitions.v1());
    }

    /** No variables at all -- checkMsmcRequestId/checkFullySigned default every decision variable
     *  their downstream gateways reference (see CheckMsmcRequestIdHandler/CheckFullySignedHandler's
     *  own Javadoc: an entirely-absent SpEL variable throws, not evaluates as null, so a bare start
     *  genuinely needs this defaulting, the same reason flowable-demo's own delegates default
     *  theirs): checkMsmcRequestId -> typeGateway ("credit", the when-branch) -> leasingGateway
     *  (isLeasing=false, default) -> insuranceDemandGateway (non-null, default) -> checkFullySigned
     *  -> fullySignedGateway (fullySigned=true, default) -> waitForSignature (the actual TIMER_WAIT
     *  step, PT10S) -> sendAgiEmail -> logEnd -> end. */
    @Test
    void aBareStartReachesTheEndViaTheRealTimerWait() {
        deploySendPdf();

        var instance = engine.start(SendPdfProcessDefinitions.PROCESS_KEY, Map.of());

        // Still short of the 10s wait -- confirms the instance is genuinely paused, not racing
        // straight through (a real bug here would otherwise pass the final assertion anyway).
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.RUNNING));

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(instanceRepo
                                .findById(instance.getId())
                                .orElseThrow()
                                .getStatus())
                        .isEqualTo(InstanceStatus.COMPLETED));
    }

    /** ADR-023/ADR-024's whole point, live: a BusinessError carrying the exact code the on-failure
     *  route matches is caught before ever reaching the default saga compensation, and routes
     *  through the shared rollback SUB_PROCESS, ending the instance COMPLETED (not FAILED) --
     *  same as activate-insurance's own routed case. Uses the leasing branch (isLeasing=true) so
     *  sendLeasingEmail is reached immediately, without waiting out the timer. */
    @Test
    void aMatchingBusinessErrorRoutesThroughTheSharedRollbackSubProcess() {
        deploySendPdf();

        var instance = engine.start(
                SendPdfProcessDefinitions.PROCESS_KEY,
                Map.of(
                        "type", "credit",
                        "isLeasing", true,
                        "action", "cancel",
                        "sendLeasingEmailFail", true,
                        "sendLeasingEmailFailErrorCode", "ROLLBACK_ERROR"));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                        instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.COMPLETED));
    }

    /** The other half of the same pattern: a plain technical exception on the exact same guarded
     *  step is NOT the ROLLBACK_ERROR the route matches on, so it falls through to the engine's
     *  default global saga compensation instead -- the instance ends FAILED, no rollback ever
     *  starts, same as activate-insurance's own unrouted case. */
    @Test
    void aNonMatchingTechnicalFailureFallsThroughToDefaultCompensation() {
        deploySendPdf();

        var instance = engine.start(
                SendPdfProcessDefinitions.PROCESS_KEY,
                Map.of("type", "credit", "isLeasing", true, "sendLeasingEmailFail", true));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                        instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.FAILED));
    }
}
