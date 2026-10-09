package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.server.application.port.CategoryCatalogPort;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:statement_import;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.password=", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "finance.security.reader-password=reader-secret-for-test-12345",
    "finance.security.admin-password=admin-secret-for-test-67890",
    "finance.security.importer-password=importer-secret-for-test-54321"
})
@AutoConfigureMockMvc
@Sql("/ingestion-schema.sql")
class StatementImportIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean CategoryCatalogPort catalog;

  @BeforeEach
  void setup() {
    jdbc.update("INSERT INTO product(id,name,type,currency,balance,balance_at,provider) VALUES ('account','Cuenta Kutxabank','ACCOUNT','EUR',1000,'2026-10-01 00:00:00','KUTXABANK')");
    when(catalog.findActiveByCode("ALIMENTACION")).thenReturn(Optional.of(
        new CategoryCatalogPort.Category("ALIMENTACION", "Alimentación",
            List.of(new CategoryCatalogPort.Subcategory("SUPERMERCADO", "Supermercado")))));
    jdbc.update("INSERT INTO classification_rule VALUES ('merchant_shop',10,'MERCHANT','MERCADONA','ALIMENTACION','SUPERMERCADO','NORMAL',0.99)");
  }

  @Test
  void nativeUploadClassifiesPreservesSignsAndIsIdempotent() throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(multipart("/api/products/account/imports")
          .file(workbook("09/10/2026")) .param("format", "KUTXABANK_ACCOUNT_XLS")
          .with(user("admin").roles("ADMIN", "READER")))
          .andExpect(status().isOk()).andExpect(jsonPath("read").value(3))
          .andExpect(jsonPath("inserted").value(attempt == 0 ? 3 : 0))
          .andExpect(jsonPath("duplicates").value(attempt == 0 ? 0 : 3));
      if (attempt == 0)
        jdbc.update("UPDATE movement SET category='MANUAL_TEST',classification_source='MANUAL' WHERE amount=1000");
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement", Integer.class)).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement WHERE amount=-10 AND category='ALIMENTACION' AND subcategory='SUPERMERCADO' AND classification_source='MERCHANT_RULE'", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT category FROM movement WHERE amount=1000", String.class)).isEqualTo("MANUAL_TEST");
    assertThat(jdbc.queryForObject("SELECT balance FROM product", BigDecimal.class)).isEqualByComparingTo("1000");
  }

  @Test
  void formatsAreLimitedToProductProviderAndType() throws Exception {
    mvc.perform(get("/api/products/account/import-formats").with(user("reader").roles("READER")))
        .andExpect(status().isOk()).andExpect(jsonPath("length()").value(2))
        .andExpect(jsonPath("[1].id").value("KUTXABANK_ACCOUNT_XLS"));
    jdbc.update("UPDATE product SET type='CREDIT_CARD',provider='ING'");
    mvc.perform(get("/api/products/account/import-formats").with(user("reader").roles("READER")))
        .andExpect(status().isOk()).andExpect(jsonPath("[1].id").value("ING_CREDIT_CARD_XLS"));
    jdbc.update("UPDATE product SET type='WALLET',provider='PAYPAL'");
    mvc.perform(get("/api/products/account/import-formats").with(user("reader").roles("READER")))
        .andExpect(status().isOk()).andExpect(jsonPath("length()").value(1))
        .andExpect(jsonPath("[0].id").value("CSV"));
  }

  @Test
  void incompatibleBankOrAccountLayoutDoesNotWriteAnything() throws Exception {
    for (String format : List.of("ING_ACCOUNT_XLS", "KUTXABANK_CARD_XLS", "UNKNOWN"))
      mvc.perform(multipart("/api/products/account/imports").file(workbook("09/10/2026"))
          .param("format", format).with(user("admin").roles("ADMIN", "READER")))
          .andExpect(status().isBadRequest());
    jdbc.update("UPDATE product SET type='CREDIT_CARD'");
    mvc.perform(multipart("/api/products/account/imports").file(workbook("09/10/2026"))
        .param("format", "KUTXABANK_CARD_XLS").with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement", Integer.class)).isZero();
  }

  @Test
  void invalidDateRejectsWholeStatement() throws Exception {
    mvc.perform(multipart("/api/products/account/imports").file(workbook("31/09/2026"))
        .param("format", "KUTXABANK_ACCOUNT_XLS").with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("detail").value("No se pudo normalizar el Excel nativo: Movimiento nativo inválido en fila 9"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement", Integer.class)).isZero();
  }

  @Test
  void readerCannotImportAndMissingProductCannotBeCreatedByUpload() throws Exception {
    mvc.perform(multipart("/api/products/account/imports").file(workbook("09/10/2026"))
        .param("format", "KUTXABANK_ACCOUNT_XLS").with(user("reader").roles("READER")))
        .andExpect(status().isForbidden());
    mvc.perform(multipart("/api/products/unknown/imports").file(workbook("09/10/2026"))
        .param("format", "KUTXABANK_ACCOUNT_XLS").with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movement", Integer.class)).isZero();
  }

  @Test
  void canonicalCsvRemainsCompatibleWithoutFormatParameter() throws Exception {
    String csv = "external_id;date;amount;currency;description;merchant;category;kind;status\n"
        + "stable;2026-10-09;-12.50;EUR;Compra;MERCADONA;UNCLASSIFIED;NORMAL;BOOKED\n";
    mvc.perform(multipart("/api/products/account/imports")
        .file(new MockMultipartFile("file", "movements.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
        .with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isOk()).andExpect(jsonPath("inserted").value(1));
  }

  @Test
  void emptyOrWrongExtensionHasUsefulValidationError() throws Exception {
    mvc.perform(multipart("/api/products/account/imports")
        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
        .with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("detail").value("El fichero está vacío"));
    mvc.perform(multipart("/api/products/account/imports").file(workbook("09/10/2026"))
        .with(user("admin").roles("ADMIN", "READER")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("detail").value("Selecciona un fichero .csv para este formato"));
  }

  private MockMultipartFile workbook(String lastDate) throws Exception {
    try (var book = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = book.createSheet("Listado");
      var header = sheet.createRow(6);
      String[] columns = {"fecha", "concepto", "fecha valor", "importe", "saldo"};
      for (int column = 0; column < columns.length; column++) header.createCell(column).setCellValue(columns[column]);
      for (int index = 0; index < 3; index++) {
        var row = sheet.createRow(index + 7);
        row.createCell(0).setCellValue(index == 1 ? lastDate : "09/10/2026");
        row.createCell(1).setCellValue(index == 1 ? "Nómina" : "MERCADONA");
        row.createCell(2).setCellValue("09/10/2026");
        row.createCell(3).setCellValue(index == 1 ? 1000 : -10);
        row.createCell(4).setCellValue(1000);
      }
      book.write(output);
      return new MockMultipartFile("file", "statement.xls", "application/vnd.ms-excel", output.toByteArray());
    }
  }
}
