package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoAdvisoryListenerTest {

    @Mock
    private DemoWarningBroadcaster broadcaster;

    @Captor
    private ArgumentCaptor<DemoWarning> warningCaptor;

    private DemoAdvisoryListener listener;
    private UUID instanceId;

    @BeforeEach
    void setUp() {
        listener = new DemoAdvisoryListener(broadcaster);
        instanceId = UUID.randomUUID();
    }

    @Test
    void onSlaBreachedBroadcastsTheOrderIdFromVariables() {
        listener.onSlaBreached(instanceId, "order-fulfillment", "reviewOrder", Map.of("orderId", "ORD-1"));

        verify(broadcaster).broadcast(warningCaptor.capture());
        var warning = warningCaptor.getValue();
        assertThat(warning.type()).isEqualTo("SLA breached");
        assertThat(warning.orderId()).isEqualTo("ORD-1");
        assertThat(warning.processKey()).isEqualTo("order-fulfillment");
        assertThat(warning.stepKey()).isEqualTo("reviewOrder");
        assertThat(warning.detail()).isNull();
    }

    @Test
    void onSlaBreachedFallsBackToTheInstanceIdWhenNoOrderIdVariableExists() {
        listener.onSlaBreached(instanceId, "some-other-process", "step", Map.of());

        verify(broadcaster).broadcast(warningCaptor.capture());
        assertThat(warningCaptor.getValue().orderId()).isEqualTo(instanceId.toString());
    }

    @Test
    void onDispatchAmbiguousBroadcastsWithNoDetail() {
        listener.onDispatchAmbiguous(instanceId, "order-fulfillment", "chargePayment", Map.of("orderId", "ORD-2"));

        verify(broadcaster).broadcast(warningCaptor.capture());
        var warning = warningCaptor.getValue();
        assertThat(warning.type()).isEqualTo("Dispatch ambiguous");
        assertThat(warning.orderId()).isEqualTo("ORD-2");
        assertThat(warning.stepKey()).isEqualTo("chargePayment");
        assertThat(warning.detail()).isNull();
    }

    @Test
    void onCompensationFailedBroadcastsWithTheErrorAsDetail() {
        listener.onCompensationFailed(
                instanceId, "order-fulfillment", "chargePayment", "refund API down", Map.of("orderId", "ORD-3"));

        verify(broadcaster).broadcast(warningCaptor.capture());
        var warning = warningCaptor.getValue();
        assertThat(warning.type()).isEqualTo("Compensation failed");
        assertThat(warning.orderId()).isEqualTo("ORD-3");
        assertThat(warning.detail()).isEqualTo("refund API down");
    }
}
