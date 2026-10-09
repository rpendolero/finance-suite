package com.finance.server;

import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.MovementManagementService;
import com.finance.server.infrastructure.adapter.in.rest.MovementManagementController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovementDeletionControllerTest {
  @Test void deletesOnlyTheRequestedMovementAndReturnsNoContent() throws Exception {
    var ledger = mock(LedgerPort.class);
    var mvc = MockMvcBuilders.standaloneSetup(new MovementManagementController(new MovementManagementService(ledger))).build();
    mvc.perform(delete("/api/movements/movement-id")).andExpect(status().isNoContent());
    verify(ledger).deleteMovement("movement-id");
  }
}
