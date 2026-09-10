package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.VariableType;
import com.example.processengine.dto.VariableSpec;
import org.junit.jupiter.api.Test;

/** Every bundled demo variable (the business-decision/retry ones and every step's generic
 *  "&lt;stepKey&gt;Fail"/"&lt;stepKey&gt;Slow" switches) must be declared here -- it's the only way
 *  a process-agnostic UI (admin console, demo page) can render a real form instead of a raw JSON
 *  textarea for them. */
class DemoProcessDefinitionsTest {

    @Test
    void v1DeclaresTheBusinessVariablesAndOneFailSwitchPerServiceTaskStep() {
        var names = DemoProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names)
                .containsExactlyInAnyOrder(
                        "orderId",
                        "product",
                        "price",
                        "simulateDeclined",
                        "chargePaymentFailAttempts",
                        "validateOrderFail",
                        "chargePaymentFail",
                        "reserveInventoryFail",
                        "notifyWarehouseFail",
                        "cancelOrderFail",
                        "generateInvoiceFail",
                        "validateOrderSlow",
                        "chargePaymentSlow",
                        "reserveInventorySlow",
                        "notifyWarehouseSlow",
                        "cancelOrderSlow",
                        "generateInvoiceSlow");
    }

    @Test
    void productIsDeclaredAsAStringWithADefault() {
        var product = DemoProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("product"))
                .findFirst()
                .orElseThrow();

        assertThat(product.type()).isEqualTo(VariableType.STRING);
        assertThat(product.defaultValue()).isEqualTo("Widget");
    }

    @Test
    void priceIsDeclaredAsANumberWithADefault() {
        var price = DemoProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("price"))
                .findFirst()
                .orElseThrow();

        assertThat(price.type()).isEqualTo(VariableType.NUMBER);
        assertThat(price.defaultValue()).isEqualTo(19.99);
    }

    @Test
    void v2AlsoDeclaresAFailSwitchForItsExtraStep() {
        var names = DemoProcessDefinitions.v2().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names).contains("sendConfirmationEmailFail");
    }

    @Test
    void v2AlsoDeclaresASlowSwitchForItsExtraStep() {
        var names = DemoProcessDefinitions.v2().startVariablesOrEmpty().stream()
                .map(VariableSpec::name)
                .toList();

        assertThat(names).contains("sendConfirmationEmailSlow");
    }

    @Test
    void orderIdIsDeclaredAsAStringWithADefault() {
        var orderId = DemoProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("orderId"))
                .findFirst()
                .orElseThrow();

        assertThat(orderId.type()).isEqualTo(VariableType.STRING);
        assertThat(orderId.defaultValue()).isEqualTo("ORD-1");
    }

    @Test
    void chargePaymentFailAttemptsIsDeclaredAsANumber() {
        var attempts = DemoProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("chargePaymentFailAttempts"))
                .findFirst()
                .orElseThrow();

        assertThat(attempts.type()).isEqualTo(VariableType.NUMBER);
    }

    /** These render inline next to the field/checkbox in the start-instance form (both demo and
     *  admin UI) -- long enough to actually explain, short enough to fit on one line. */
    @Test
    void everyDeclaredVariableInEitherVersionHasAShortNonBlankDescription() {
        var all = new java.util.ArrayList<>(DemoProcessDefinitions.v1().startVariablesOrEmpty());
        all.addAll(DemoProcessDefinitions.v2().startVariablesOrEmpty());

        for (var v : all) {
            assertThat(v.description()).as("description of %s", v.name()).isNotBlank();
            assertThat(v.description().length())
                    .as("description length of %s", v.name())
                    .isLessThanOrEqualTo(60);
        }
    }
}
