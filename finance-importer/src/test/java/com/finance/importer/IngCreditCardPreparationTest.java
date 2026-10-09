package com.finance.importer;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.Product;
import com.finance.statements.NativeXlsStatementPreparationAdapter;
import java.nio.file.*;
import java.time.LocalDate;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngCreditCardPreparationTest {
  @TempDir Path dir;

  private Path fixture(String status) throws Exception {
    var input = dir.resolve("credit.xls");
    try (var book = new HSSFWorkbook()) {
      var sheet = book.createSheet("Tarjetas");
      sheet.createRow(0).createCell(0).setCellValue("Tarjeta Crédito");
      String[] names = {
        "FECHA VALOR",
        "CATEGORÍA",
        "SUBCATEGORÍA",
        "DESCRIPCION",
        "COMENTARIO",
        "",
        "ESTADO",
        "IMPORTE (€)"
      };
      var header = sheet.createRow(4);
      for (int c = 0; c < names.length; c++) header.createCell(c).setCellValue(names[c]);
      var style = book.createCellStyle();
      style.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
      for (int i = 5; i < 7; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue(LocalDate.of(2026, 9, 4));
        row.getCell(0).setCellStyle(style);
        row.createCell(1).setCellValue("Compras");
        row.createCell(3).setCellValue("TIENDA    MADRID");
        row.createCell(6).setCellValue(status);
        row.createCell(7).setCellValue(-28.86);
      }
      try (var out = Files.newOutputStream(input)) {
        book.write(out);
      }
    }
    return input;
  }

  @Test
  void routesCardAndPreservesIdenticalPurchases() throws Exception {
    var adapter = new NativeXlsStatementPreparationAdapter();
    var input = fixture("Liquidado");
    var first = adapter.prepare(input, Product.Provider.ING);
    var second = adapter.prepare(input, Product.Provider.ING);
    var lines = Files.readAllLines(first);
    assertThat(lines).hasSize(3);
    assertThat(Files.readString(first)).isEqualTo(Files.readString(second));
    assertThat(lines.get(1)).contains("2026-09-04;-28.86;EUR;TIENDA MADRID;;Compras;NORMAL;BOOKED");
    assertThat(lines.get(1).split(";")[0]).isNotEqualTo(lines.get(2).split(";")[0]);
  }

  @Test
  void unknownStatusStopsImportAndRemovesOutput() throws Exception {
    var input = fixture("Estado no verificado");
    assertThatThrownBy(
            () -> new NativeXlsStatementPreparationAdapter().prepare(input, Product.Provider.ING))
        .isInstanceOf(IllegalArgumentException.class);
    try (var files = Files.list(dir)) {
      assertThat(
              files.noneMatch(p -> p.getFileName().toString().startsWith("ing-card-normalized-")))
          .isTrue();
    }
  }

  @Test
  void suppliedCreditExportWhenConfigured() throws Exception {
    String sample = System.getProperty("ing.card.sample");
    org.junit.jupiter.api.Assumptions.assumeTrue(sample != null);
    var result =
        new NativeXlsStatementPreparationAdapter().prepare(Path.of(sample), Product.Provider.ING);
    try {
      var lines = Files.readAllLines(result);
      assertThat(lines).hasSize(4);
      assertThat(lines.subList(1, 4)).allMatch(s -> s.endsWith(";NORMAL;BOOKED"));
      assertThat(lines.get(1)).contains(";-26.56;EUR;");
    } finally {
      Files.deleteIfExists(result);
    }
  }
}
