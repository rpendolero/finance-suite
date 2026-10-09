package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finance.server.application.port.CategoryCatalogPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ingestion;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "finance.security.reader-password=reader-secret-for-test-12345",
      "finance.security.admin-password=admin-secret-for-test-67890",
      "finance.security.importer-password=importer-secret-for-test-54321"
    })
@AutoConfigureMockMvc
@Sql("/ingestion-schema.sql")
class IngestionIntegrationTest {
  @MockitoBean CategoryCatalogPort catalog;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  String product =
      "{\"id\":\"a\",\"name\":\"ING\",\"provider\":\"ING\",\"type\":\"ACCOUNT\",\"currency\":\"EUR\",\"balance\":1000.00,\"balanceAt\":\"2026-10-02T06:00:00Z\"}";
  String csv =
      "external_id;date;amount;currency;description;merchant;category;kind;status\n"
          + "id;2026-09-10;-10.00;EUR;Compra;Shop;FOOD;NORMAL;BOOKED\n";

  MockMultipartFile snapshot(String value) {
    return new MockMultipartFile(
        "product", "product.json", "application/json", value.getBytes(StandardCharsets.UTF_8));
  }

  MockMultipartFile file(String value) {
    return new MockMultipartFile(
        "file", "movements.csv", "text/csv", value.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void uploadsAreAtomicAndIdempotent() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(
              multipart("/api/importer/products/a/batches")
                  .file(snapshot(product))
                  .file(file(csv))
                  .with(user("importer").roles("IMPORTER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("inserted").value(i == 0 ? 1 : 0));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT provider FROM product", String.class)).isEqualTo("ING");
  }

  @Test
  void malformedCsvRollsBackNewProduct() throws Exception {
    mvc.perform(
            multipart("/api/importer/products/a/batches")
                .file(snapshot(product))
                .file(file("incorrect\n"))
                .with(user("importer").roles("IMPORTER")))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isZero();
  }

  @Test
  void olderSnapshotCannotOverwriteFreshBalance() throws Exception {
    mvc.perform(
            multipart("/api/importer/products/a/batches")
                .file(snapshot(product))
                .file(file(csv))
                .with(user("importer").roles("IMPORTER")))
        .andExpect(status().isOk());
    mvc.perform(
            multipart("/api/importer/products/a/batches")
                .file(snapshot(product.replace("2026-10-02", "2026-10-01")))
                .file(file(csv))
                .with(user("importer").roles("IMPORTER")))
        .andExpect(status().isBadRequest());
  }
}
