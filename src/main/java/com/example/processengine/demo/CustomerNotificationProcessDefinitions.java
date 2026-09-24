package com.example.processengine.demo;

import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.dto.StepSpecs;
import com.example.processengine.dto.TransitionSpecs;
import java.util.List;

/** A genuinely separate, independently-deployable process -- called from order-fulfillment v2's
 *  own {@code sendConfirmationEmail} SUB_PROCESS step rather than being a plain step there.
 *  Demonstrates nesting: an activity that is itself a whole process, with its own start/end and
 *  internal steps, not a special "sub-process" element embedded inline. Kept deliberately small
 *  (prepare, then send) since its purpose is the nesting itself, not notification logic depth --
 *  the same reasoning flowable-demo's own warehouse-fulfillment.bpmn20.xml documents for the
 *  equivalent Flowable-side example. See ADR-022. */
public final class CustomerNotificationProcessDefinitions {

    public static final String PROCESS_KEY = "customer-notification";

    private CustomerNotificationProcessDefinitions() {}

    public static ProcessDefinitionSpec v1() {
        return new ProcessDefinitionSpec(
                "Customer Notification",
                "prepareEmail",
                List.of(
                        StepSpecs.serviceTask("prepareEmail", "Prepare email", "prepareEmail"),
                        StepSpecs.serviceTask("sendEmail", "Send email", "sendEmail"),
                        StepSpecs.end("end", "End")),
                List.of(TransitionSpecs.to("prepareEmail", "sendEmail"), TransitionSpecs.to("sendEmail", "end")),
                List.of());
    }
}
