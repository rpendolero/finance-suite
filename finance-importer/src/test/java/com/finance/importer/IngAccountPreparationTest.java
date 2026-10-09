package com.finance.importer;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.Product;
import com.finance.statements.NativeXlsStatementPreparationAdapter;
import java.nio.file.*;
import java.time.LocalDate;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngAccountPreparationTest {
  @TempDir Path dir;

  private Path fixture() throws Exception {
    Path path = dir.resolve("ing.xls");
    try (var book = new HSSFWorkbook()) {
      var sheet = book.createSheet("Movimientos");
      var header = sheet.createRow(3);
      String[] names = {
        "F. VALOR",
        "CATEGORÍA",
        "SUBCATEGORÍA",
        "DESCRIPCIÓN",
        "COMENTARIO",
        "IMPORTE (€)",
        "SALDO (€)"
      };
      for (int c = 0; c < names.length; c++) header.createCell(c).setCellValue(names[c]);
      var style = book.createCellStyle();
      style.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
      for (int i = 4; i < 7; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue(LocalDate.of(2026, 9, 1));
        row.getCell(0).setCellStyle(style);
        row.createCell(1).setCellValue("Compras");
        row.createCell(3).setCellValue("Compra; ejemplo");
        row.createCell(5).setCellValue(-9.99);
        row.createCell(6).setCellValue(i == 6 ? 80.02 : 90.01);
      }
      try (var out = Files.newOutputStream(path)) {
        book.write(out);
      }
    }
    return path;
  }

  @Test
  void keepsRepeatedPurchasesAndIsStable() throws Exception {
    var adapter = new NativeXlsStatementPreparationAdapter();
    var input = fixture();
    var first = adapter.prepare(input, Product.Provider.ING);
    var second = adapter.prepare(input, Product.Provider.ING);
    assertThat(Files.readString(first)).isEqualTo(Files.readString(second));
    var rows = Files.readAllLines(first);
    assertThat(rows).hasSize(4);
    assertThat(rows.get(1)).contains("2026-09-01;-9.99;EUR;\"Compra; ejemplo\"");
    assertThat(rows.stream().skip(1).map(r -> r.split(";")[0]).distinct().count()).isEqualTo(3);
    assertThat(input).exists();
  }

  @Test
  void rejectsWrongProvider() throws Exception {
    assertThatThrownBy(
            () ->
                new NativeXlsStatementPreparationAdapter()
                    .prepare(fixture(), Product.Provider.PAYPAL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedWorkbookAndDeletesPartialOutput() throws Exception {
    var input = fixture();
    try (var stream = Files.newInputStream(input);
        var book = new HSSFWorkbook(stream)) {
      book.getSheetAt(0).getRow(5).getCell(5).setCellValue("invalid");
      try (var out = Files.newOutputStream(dir.resolve("bad.xls"))) {
        book.write(out);
      }
    }
    assertThatThrownBy(
            () ->
                new NativeXlsStatementPreparationAdapter()
                    .prepare(dir.resolve("bad.xls"), Product.Provider.ING))
        .isInstanceOf(IllegalArgumentException.class);
    try (var files = Files.list(dir)) {
      assertThat(files.noneMatch(p -> p.getFileName().toString().startsWith("ing-normalized-")))
          .isTrue();
    }
  }

  @Test
  void suppliedRealExportWhenExplicitlyConfigured() throws Exception {
    String path = System.getProperty("ing.sample");
    org.junit.jupiter.api.Assumptions.assumeTrue(path != null);
    var result =
        new NativeXlsStatementPreparationAdapter().prepare(Path.of(path), Product.Provider.ING);
    try {
      assertThat(Files.readAllLines(result)).hasSize(73);
    } finally {
      Files.deleteIfExists(result);
    }
  }
}
