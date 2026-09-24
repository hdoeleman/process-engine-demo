package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import com.example.processengine.dto.VariableSpec;
import com.example.processengine.dto.VariableSpecs;
import java.util.ArrayList;
import java.util.List;

/** The order-fulfillment diagram, as JSON-deployable specs. v2 adds a step to demonstrate
 *  versioning. To demonstrate saga compensation unwinding an otherwise-successful run, deploy
 *  either version and set "&lt;stepKey&gt;Fail": true (e.g. "generateInvoiceFail") as a start
 *  variable -- every bundled handler honors that convention (see FailSwitch). Set
 *  "&lt;stepKey&gt;Slow": true (e.g. "chargePaymentSlow") the same way to make that step pause 11s
 *  instead -- combined with a short per-step timeout (admin console's step-config editor), this
 *  exercises the timeout-then-retry path (see PauseSwitch, RetryBackoff) without a permanent
 *  failure. Every variable both versions actually understand is declared via {@code
 *  startVariables} -- that's what lets the process-agnostic admin console (and this demo's own
 *  page) render a real start-instance form instead of a raw JSON textarea. */
public final class DemoProcessDefinitions {

    public static final String PROCESS_KEY = "order-fulfillment";

    private static final int CHARGE_PAYMENT_MAX_ATTEMPTS = 3;
    private static final long CHARGE_PAYMENT_RETRY_BACKOFF_MILLIS = 200;

    private DemoProcessDefinitions() {}

    private static final List<VariableSpec> BASE_VARIABLES = List.of(
            VariableSpecs.text("orderId", "Order identifier used across the run", "ORD-1"),
            VariableSpecs.text("product", "Product being ordered", "Widget"),
            VariableSpecs.number("price", "Unit price of the product", 19.99),
            VariableSpecs.bool("simulateDeclined", "Declines the payment (not a failure)"),
            VariableSpecs.number("chargePaymentFailAttempts", "Retries chargePayment this many times before success"));

    /** One generic "make it fail permanently" switch and one "make it pause 11s" switch per
     *  service-task step key -- see FailSwitch/PauseSwitch. */
    private static List<VariableSpec> withTestingSwitches(String... serviceTaskStepKeys) {
        List<VariableSpec> variables = new ArrayList<>(BASE_VARIABLES);
        for (String stepKey : serviceTaskStepKeys) {
            variables.add(VariableSpecs.bool(stepKey + "Fail", "Fails permanently (tests saga rollback)"));
            variables.add(VariableSpecs.bool(stepKey + "Slow", "Pauses 11s (tests timeout + retry)"));
        }
        return variables;
    }

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Order Fulfillment",
                "validateOrder",
                List.of(
                        StepSpecs.serviceTask("validateOrder", "Validate order", "validateOrder"),
                        StepSpecs.userTask("reviewOrder", "Review order"),
                        StepSpecs.parallelFork("fork", "Fork"),
                        StepSpecs.serviceTask(
                                "chargePayment",
                                "Charge payment",
                                "chargePayment",
                                CHARGE_PAYMENT_MAX_ATTEMPTS,
                                CHARGE_PAYMENT_RETRY_BACKOFF_MILLIS),
                        StepSpecs.serviceTask("reserveInventory", "Reserve inventory", "reserveInventory"),
                        StepSpecs.serviceTask("notifyWarehouse", "Notify warehouse", "notifyWarehouse"),
                        StepSpecs.parallelJoin("join", "Join: all branches done"),
                        StepSpecs.exclusiveGateway("paymentGateway", "Payment ok?"),
                        StepSpecs.serviceTask("cancelOrder", "Cancel order", "cancelOrder"),
                        StepSpecs.serviceTask("generateInvoice", "Generate invoice", "generateInvoice"),
                        StepSpecs.end("end", "End")),
                List.of(
                        TransitionSpecs.to("validateOrder", "reviewOrder"),
                        TransitionSpecs.to("reviewOrder", "fork"),
                        TransitionSpecs.to("fork", "chargePayment"),
                        TransitionSpecs.to("fork", "reserveInventory"),
                        TransitionSpecs.to("fork", "notifyWarehouse"),
                        TransitionSpecs.to("chargePayment", "join"),
                        TransitionSpecs.to("reserveInventory", "join"),
                        TransitionSpecs.to("notifyWarehouse", "join"),
                        TransitionSpecs.to("join", "paymentGateway"),
                        TransitionSpecs.when("paymentGateway", "cancelOrder", "paymentApproved == false"),
                        TransitionSpecs.defaultBranch("paymentGateway", "generateInvoice"),
                        TransitionSpecs.to("cancelOrder", "end"),
                        TransitionSpecs.to("generateInvoice", "end")),
                withTestingSwitches(
                        "validateOrder",
                        "chargePayment",
                        "reserveInventory",
                        "notifyWarehouse",
                        "cancelOrder",
                        "generateInvoice"));
    }

    /** Same graph, plus a "send confirmation email" step between invoice generation and the end. */
    public static ProcessDefinitionSpec v2() {
        return new ProcessDefinitionSpec(
                "Order Fulfillment",
                "validateOrder",
                List.of(
                        StepSpecs.serviceTask("validateOrder", "Validate order", "validateOrder"),
                        StepSpecs.userTask("reviewOrder", "Review order"),
                        StepSpecs.parallelFork("fork", "Fork"),
                        StepSpecs.serviceTask(
                                "chargePayment",
                                "Charge payment",
                                "chargePayment",
                                CHARGE_PAYMENT_MAX_ATTEMPTS,
                                CHARGE_PAYMENT_RETRY_BACKOFF_MILLIS),
                        StepSpecs.serviceTask("reserveInventory", "Reserve inventory", "reserveInventory"),
                        StepSpecs.serviceTask("notifyWarehouse", "Notify warehouse", "notifyWarehouse"),
                        StepSpecs.parallelJoin("join", "Join: all branches done"),
                        StepSpecs.exclusiveGateway("paymentGateway", "Payment ok?"),
                        StepSpecs.serviceTask("cancelOrder", "Cancel order", "cancelOrder"),
                        StepSpecs.serviceTask("generateInvoice", "Generate invoice", "generateInvoice"),
                        // Nested process, not a plain step: customer notification is a whole
                        // separate process (CustomerNotificationProcessDefinitions, its own
                        // start/end and internal steps), auto-deployed by DemoDataLoader the same
                        // way order-fulfillment itself is. calledProcessKey resolves by process
                        // key to whichever version is currently PUBLISHED, matching this engine's
                        // own "any published version is startable" semantics -- no separate
                        // pinning mechanism added for the sub-process. The child gets a full copy
                        // of the parent's own variables to start, and its own final variables
                        // (including confirmationEmailSent) are copied back once it completes --
                        // see ADR-022 for why this whole-map-copy is a deliberate simplification,
                        // not a per-field mapping DSL the way Flowable's flowable:in/out is.
                        StepSpecs.subProcess(
                                "sendConfirmationEmail", "Send confirmation email", "customer-notification"),
                        StepSpecs.end("end", "End")),
                List.of(
                        TransitionSpecs.to("validateOrder", "reviewOrder"),
                        TransitionSpecs.to("reviewOrder", "fork"),
                        TransitionSpecs.to("fork", "chargePayment"),
                        TransitionSpecs.to("fork", "reserveInventory"),
                        TransitionSpecs.to("fork", "notifyWarehouse"),
                        TransitionSpecs.to("chargePayment", "join"),
                        TransitionSpecs.to("reserveInventory", "join"),
                        TransitionSpecs.to("notifyWarehouse", "join"),
                        TransitionSpecs.to("join", "paymentGateway"),
                        TransitionSpecs.when("paymentGateway", "cancelOrder", "paymentApproved == false"),
                        TransitionSpecs.defaultBranch("paymentGateway", "generateInvoice"),
                        TransitionSpecs.to("cancelOrder", "end"),
                        TransitionSpecs.to("generateInvoice", "sendConfirmationEmail"),
                        TransitionSpecs.to("sendConfirmationEmail", "end")),
                // sendConfirmationEmail no longer gets a Fail/Slow switch here -- it's a
                // SUB_PROCESS now, with no handlerRef of its own for FailSwitch/PauseSwitch to
                // hook into (those work by reading a start variable inside the handler that
                // actually runs, and prepareEmail/sendEmail -- the sub-process's own steps --
                // don't implement that convention).
                withTestingSwitches(
                        "validateOrder",
                        "chargePayment",
                        "reserveInventory",
                        "notifyWarehouse",
                        "cancelOrder",
                        "generateInvoice"));
    }
}
