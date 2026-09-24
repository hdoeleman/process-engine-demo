package com.example.processengine.demo;

import com.example.processengine.engine.ProcessAdvisoryListener;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reference implementation of {@link ProcessAdvisoryListener} for this demo: every advisory
 *  situation becomes a warning pushed live into the demo UI (see {@link DemoWarningBroadcaster}),
 *  so an operator watching the page sees it the moment it happens, no polling on either side. */
@Component
public class DemoAdvisoryListener implements ProcessAdvisoryListener {

    private final DemoWarningBroadcaster broadcaster;

    public DemoAdvisoryListener(DemoWarningBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void onSlaBreached(UUID instanceId, String processKey, String stepKey, Map<String, Object> variables) {
        broadcaster.broadcast(
                new DemoWarning("SLA breached", orderIdOf(instanceId, variables), processKey, stepKey, null));
    }

    @Override
    public void onDispatchAmbiguous(UUID instanceId, String processKey, String stepKey, Map<String, Object> variables) {
        broadcaster.broadcast(
                new DemoWarning("Dispatch ambiguous", orderIdOf(instanceId, variables), processKey, stepKey, null));
    }

    @Override
    public void onCompensationFailed(
            UUID instanceId, String processKey, String stepKey, String error, Map<String, Object> variables) {
        broadcaster.broadcast(
                new DemoWarning("Compensation failed", orderIdOf(instanceId, variables), processKey, stepKey, error));
    }

    // Falls back to the raw instance id for any process that doesn't declare an "orderId"
    // variable -- this listener is written for the bundled order-fulfillment demo specifically,
    // but must not blow up if some other process key is deployed alongside it.
    private String orderIdOf(UUID instanceId, Map<String, Object> variables) {
        var orderId = variables.get("orderId");
        return orderId != null ? orderId.toString() : instanceId.toString();
    }
}
