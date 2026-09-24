package com.example.processengine.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.processengine.definition.StepType;
import com.example.processengine.dto.StepSpec;
import org.junit.jupiter.api.Test;

class MultiSupplierQuoteProcessDefinitionsTest {

    private StepSpec step(String key) {
        return MultiSupplierQuoteProcessDefinitions.v1().steps().stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void requestQuotesIsAMultiInstanceSubProcessCallingSupplierQuote() {
        var requestQuotes = step("requestQuotes");

        assertThat(requestQuotes.type()).isEqualTo(StepType.SUB_PROCESS);
        assertThat(requestQuotes.calledProcessKey()).isEqualTo(SupplierQuoteProcessDefinitions.PROCESS_KEY);
        assertThat(requestQuotes.multiInstanceCollectionVariable()).isEqualTo("suppliers");
    }

    @Test
    void everyOtherStepIsNotMultiInstance() {
        for (String key : new String[] {"prepareRequest", "summarizeQuotes", "end"}) {
            assertThat(step(key).multiInstanceCollectionVariable()).as(key).isNull();
        }
    }

    @Test
    void isALinearFlowFromPrepareRequestToEnd() {
        var transitions = MultiSupplierQuoteProcessDefinitions.v1().transitions();

        assertThat(transitions).hasSize(3);
        assertThat(transitions.stream().map(t -> t.from() + "->" + t.to()))
                .containsExactlyInAnyOrder(
                        "prepareRequest->requestQuotes", "requestQuotes->summarizeQuotes", "summarizeQuotes->end");
    }

    @Test
    void declaresSuppliersAsACommaSeparatedTextVariableWithAThreeSupplierDefault() {
        // No VariableType widget exists for a list-valued variable -- see this class's own
        // Javadoc -- so it's a plain text field, split into a real List by prepareRequest's own
        // handler before requestQuotes reads it.
        var suppliers = MultiSupplierQuoteProcessDefinitions.v1().startVariablesOrEmpty().stream()
                .filter(v -> v.name().equals("suppliers"))
                .findFirst()
                .orElseThrow();

        assertThat(suppliers.type()).isEqualTo(com.example.processengine.definition.VariableType.STRING);
        assertThat(suppliers.defaultValue()).isEqualTo("Acme Corp, Globex, Initech");
    }
}
