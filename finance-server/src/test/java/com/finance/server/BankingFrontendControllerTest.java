package com.finance.server;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finance.server.application.service.*;
import com.finance.server.application.port.*;
import java.time.Clock;
import java.util.Optional;
import com.finance.server.domain.banking.BankConnection;
import com.finance.server.infrastructure.adapter.in.rest.*;
import com.finance.server.infrastructure.config.EnableBankingProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BankingFrontendControllerTest {
  @Test
  void expiredConsentCannotContactBankForSynchronization() {
    var data = mock(BankingDataPort.class);
    var connections = mock(BankConnectionPort.class);
    when(connections.find("id")).thenReturn(Optional.of(new BankConnection("id", "ENABLE_BANKING", "Bank", "ES",
        "session", "state", Instant.parse("2020-01-01T00:00:00Z"), BankConnection.Status.ACTIVE, null)));
    var service = new BankingSyncService(data, connections, mock(LedgerPort.class), mock(SettingsPort.class), null, Clock.systemUTC());
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync("id")).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(data);
  }

  @Test
  void bankCatalogUsesConfiguredCountry() throws Exception {
    var port = mock(BankingAuthorizationPort.class);
    when(port.banks("ES")).thenReturn(List.of("Bank"));
    var service = new BankingAuthorizationService(port, mock(BankConnectionPort.class), Clock.systemUTC());
    var mvc = MockMvcBuilders.standaloneSetup(new EnableBankingController(service, new EnableBankingProperties())).build();
    mvc.perform(get("/api/v1/banking/enable-banking/banks")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("Bank"));
  }

  @Test
  void connectionsNeverExposeProviderSessionOrAuthorizationState() throws Exception {
    var connections = mock(BankConnectionPort.class);
    var sync = new BankingSyncService(mock(BankingDataPort.class), connections, mock(LedgerPort.class), mock(SettingsPort.class), null, Clock.systemUTC());
    when(connections.findAll()).thenReturn(List.of(new BankConnection("id", "ENABLE_BANKING", "Bank", "ES",
        "private-session", "private-state", Instant.parse("2027-01-01T00:00:00Z"), BankConnection.Status.ACTIVE, null)));
    var mvc = MockMvcBuilders.standaloneSetup(new BankingSyncController(sync)).build();
    mvc.perform(get("/api/v1/banking/connections")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bankName").value("Bank"))
        .andExpect(jsonPath("$[0].externalSessionId").doesNotExist())
        .andExpect(jsonPath("$[0].authorizationState").doesNotExist());
  }

  @Test
  void callbackCompletesAuthorizationAndReturnsToFrontendWithoutCode() throws Exception {
    var port = mock(BankingAuthorizationPort.class);
    var connections = mock(BankConnectionPort.class);
    var service = new BankingAuthorizationService(port, connections, Clock.systemUTC());
    when(connections.findByState("state")).thenReturn(Optional.of(new BankConnection("id", "ENABLE_BANKING", "Bank", "ES", null, "state", Instant.parse("2027-01-01T00:00:00Z"), BankConnection.Status.AUTHORIZING, null)));
    when(port.exchangeCode("secret-code")).thenReturn(new BankingAuthorizationPort.Session("private-session"));
    var config = new EnableBankingProperties();
    config.setFrontendUrl("http://localhost:5173/");
    var mvc = MockMvcBuilders.standaloneSetup(new EnableBankingController(service, config)).build();
    mvc.perform(get("/api/v1/banking/enable-banking/callback").param("code", "secret-code").param("state", "state"))
        .andExpect(status().isSeeOther()).andExpect(header().string("Location", "http://localhost:5173/?banking=connected"));
    verify(port).exchangeCode("secret-code");
  }

  @Test
  void cancelledAuthorizationReturnsToFrontendWithoutExchangingCode() throws Exception {
    var port = mock(BankingAuthorizationPort.class);
    var connections = mock(BankConnectionPort.class);
    var service = new BankingAuthorizationService(port, connections, Clock.systemUTC());
    var mvc = MockMvcBuilders.standaloneSetup(new EnableBankingController(service, new EnableBankingProperties())).build();
    mvc.perform(get("/api/v1/banking/enable-banking/callback").param("error", "access_denied"))
        .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/?banking=cancelled"));
    verifyNoInteractions(port, connections);
  }
}
