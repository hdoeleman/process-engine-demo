package com.example.processengine.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.processengine.definition.ProcessDefinitionEntity;
import com.example.processengine.dto.ProcessDefinitionSpec;
import com.example.processengine.engine.ProcessEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoRedeployControllerTest {

    /** How many processes the app bundles -- order-fulfillment plus seven others. */
    private static final int BUNDLED_PROCESS_COUNT = 8;

    @Mock
    private ProcessEngine engine;

    @Test
    void redeploysEveryBundledProcessAfterADataWipe() {
        new DemoRedeployController(engine).redeployAll();

        verify(engine, times(BUNDLED_PROCESS_COUNT)).deploy(any(), any(ProcessDefinitionSpec.class));
        verify(engine).deploy(eq(DemoProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
        verify(engine).deploy(eq(MultiSupplierQuoteProcessDefinitions.PROCESS_KEY), any(ProcessDefinitionSpec.class));
    }

    @Test
    void isASafeNoOpForAProcessThatIsStillDeployed() {
        // Only order-fulfillment is stubbed as still published; the other seven are unstubbed
        // (empty list) and so get redeployed -- order-fulfillment itself must not be. Lenient,
        // since strict stubs would flag those other keys' unstubbed calls as a mismatch.
        lenient()
                .when(engine.listPublishedVersions(DemoProcessDefinitions.PROCESS_KEY))
                .thenReturn(List.of(new ProcessDefinitionEntity(DemoProcessDefinitions.PROCESS_KEY, 1, "n", "s")));

        new DemoRedeployController(engine).redeployAll();

        verify(engine, never()).deploy(eq(DemoProcessDefinitions.PROCESS_KEY), any());
        verify(engine, times(BUNDLED_PROCESS_COUNT - 1)).deploy(any(), any(ProcessDefinitionSpec.class));
    }
}
