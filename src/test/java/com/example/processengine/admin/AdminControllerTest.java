package com.example.processengine.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.processengine.engine.ProcessDataResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private ProcessDataResetService dataResetService;

    @Test
    void clearAllDataDelegatesToTheServiceWhenEnabled() {
        var controller = new AdminController(dataResetService, true);

        controller.clearAllData();

        verify(dataResetService).clearAllData();
    }

    @Test
    void clearAllDataRefusesWhenDisabledAndNeverTouchesTheService() {
        // The Javadoc used to say "demo/dev-only" with nothing in code enforcing it -- this flag
        // is the actual enforcement, for anyone who ends up running this reachable beyond a laptop.
        var controller = new AdminController(dataResetService, false);

        assertThatThrownBy(controller::clearAllData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        verify(dataResetService, never()).clearAllData();
    }
}
