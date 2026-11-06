package com.example.processengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.demo.ActivateInsuranceProcessDefinitions;
import com.example.processengine.demo.ActivateInsuranceRollbackProcessDefinitions;
import com.example.processengine.demo.CustomerNotificationProcessDefinitions;
import com.example.processengine.demo.DemoProcessDefinitions;
import com.example.processengine.demo.MultiSupplierQuoteProcessDefinitions;
import com.example.processengine.demo.SendPdfProcessDefinitions;
import com.example.processengine.demo.SendPdfRollbackProcessDefinitions;
import com.example.processengine.demo.SupplierQuoteProcessDefinitions;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Every bundled demo definition satisfies the engine's rules, including the effect model: the declared effect types, the
 * order in which steps may run, and the sub-process declarations. The definitions are validated one by one, called
 * processes first, so a sub-process step is checked against the process it really calls.
 */
@SpringBootTest
@ActiveProfiles("test")
class BundledDefinitionsAreValidTest {

    @Autowired
    private ProcessEngine engine;

    @Test
    void everyBundledDefinitionPassesValidation() {
        Map<String, ProcessDefinitionSpec> bundled = new LinkedHashMap<>();
        bundled.put(CustomerNotificationProcessDefinitions.PROCESS_KEY, CustomerNotificationProcessDefinitions.v1());
        bundled.put(DemoProcessDefinitions.PROCESS_KEY, DemoProcessDefinitions.v1());
        bundled.put(
                ActivateInsuranceRollbackProcessDefinitions.PROCESS_KEY,
                ActivateInsuranceRollbackProcessDefinitions.v1());
        bundled.put(ActivateInsuranceProcessDefinitions.PROCESS_KEY, ActivateInsuranceProcessDefinitions.v1());
        bundled.put(SendPdfRollbackProcessDefinitions.PROCESS_KEY, SendPdfRollbackProcessDefinitions.v1());
        bundled.put(SendPdfProcessDefinitions.PROCESS_KEY, SendPdfProcessDefinitions.v1());
        bundled.put(SupplierQuoteProcessDefinitions.PROCESS_KEY, SupplierQuoteProcessDefinitions.v1());
        bundled.put(MultiSupplierQuoteProcessDefinitions.PROCESS_KEY, MultiSupplierQuoteProcessDefinitions.v1());

        List<String> problems = new ArrayList<>();
        bundled.forEach((key, spec) -> {
            engine.validateSpec(spec).messages().forEach(error -> problems.add(key + ": " + error));
            if (engine.listPublishedVersions(key).isEmpty()) {
                try {
                    engine.deploy(key, spec); // so a process that calls it is checked against it
                } catch (IllegalArgumentException e) {
                    // already reported by validateSpec above
                }
            }
        });

        engine.validateSpec(DemoProcessDefinitions.v2())
                .messages()
                .forEach(error -> problems.add("order-fulfillment v2: " + error));

        assertThat(problems).isEmpty();
    }
}
