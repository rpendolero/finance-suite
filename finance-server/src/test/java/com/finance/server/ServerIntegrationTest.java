package com.finance.server;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finance.domain.*;
import com.finance.server.infrastructure.adapter.out.persistence.JpaLedgerAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "finance.security.reader-password=reader-secret-for-test-12345",
      "finance.security.admin-password=admin-secret-for-test-67890",
      "finance.security.importer-password=importer-secret-for-test-54321"
    })
@AutoConfigureMockMvc
class ServerIntegrationTest {
  @Autowired MockMvc mvc;
  @MockitoBean JpaLedgerAdapter ledger;

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void readerCanReadButCannotWrite() throws Exception {
    when(ledger.products()).thenReturn(List.of());
    mvc.perform(get("/api/products").with(httpBasic("reader", "reader-secret-for-test-12345")))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    mvc.perform(delete("/api/products/a").with(httpBasic("reader", "reader-secret-for-test-12345")))
        .andExpect(status().isForbidden());
  }

  @Test
  void importerCannotQueryOrDelete() throws Exception {
    mvc.perform(get("/api/products").with(httpBasic("importer", "importer-secret-for-test-54321")))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/products/a").with(httpBasic("importer", "importer-secret-for-test-54321")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/mcp")
                .with(httpBasic("importer", "importer-secret-for-test-54321"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void readerCannotIngest() throws Exception {
    mvc.perform(
            post("/api/importer/products/a/batches")
                .with(httpBasic("reader", "reader-secret-for-test-12345")))
        .andExpect(status().isForbidden());
  }

  @Test
  void crossOriginBrowserRequestsAreBlocked() throws Exception {
    mvc.perform(
            get("/api/products")
                .header("Origin", "https://other.example")
                .with(httpBasic("reader", "reader-secret-for-test-12345")))
        .andExpect(status().isForbidden());
  }

  @Test
  void mcpInitializesUsingReaderAuthentication() throws Exception {
    mvc.perform(
            post("/mcp")
                .with(httpBasic("reader", "reader-secret-for-test-12345"))
                .contentType("application/json")
                .header("Accept", "application/json, text/event-stream")
                .content(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Mcp-Session-Id"));
  }
}
