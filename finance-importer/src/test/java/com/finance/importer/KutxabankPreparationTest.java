package com.finance.importer;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.finance.domain.Product;
import com.finance.importer.infrastructure.adapter.out.csv.NativeXlsStatementPreparationAdapter;
import java.nio.file.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KutxabankPreparationTest {
  @TempDir Path dir;

  private Path fixture(String date) throws Exception {
    Path input = dir.resolve("statement.xls");
    try (var book = new HSSFWorkbook()) {
      var sheet = book.createSheet("Listado");
      var header = sheet.createRow(6);
      String[] labels = {"fecha", "concepto", "fecha valor", "importe", "saldo"};
      for (int c = 0; c < labels.length; c++) header.createCell(c).setCellValue(labels[c]);
      for (int i = 8; i < 11; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue(date);
        row.createCell(1).setCellValue("Compra; ejemplo");
        row.createCell(2).setCellValue("02/09/2026");
        row.createCell(3).setCellValue(-9.99);
        row.createCell(4).setCellValue(i == 10 ? 80.02 : 90.01);
      }
      try (var out = Files.newOutputStream(input)) {
        book.write(out);
      }
    }
    return input;
  }

  @Test
  void convertsTextDatesAndPreservesRepeatedMovements() throws Exception {
    var adapter = new NativeXlsStatementPreparationAdapter();
    var input = fixture("01/09/2026");
    var first = adapter.prepare(input, Product.Provider.KUTXABANK);
    var second = adapter.prepare(input, Product.Provider.KUTXABANK);
    assertThat(Files.readString(first)).isEqualTo(Files.readString(second));
    var rows = Files.readAllLines(first);
    assertThat(rows).hasSize(4);
    assertThat(rows.get(1))
        .contains("2026-09-01;-9.99;EUR;\"Compra; ejemplo\";;UNCLASSIFIED;NORMAL;BOOKED");
    assertThat(rows.stream().skip(1).map(row -> row.split(";")[0]).distinct().count()).isEqualTo(3);
    assertThat(input).exists();
  }

  @Test
  void rejectsInvalidCalendarDateAndRemovesPartialOutput() throws Exception {
    var input = fixture("31/09/2026");
    assertThatThrownBy(
            () ->
                new NativeXlsStatementPreparationAdapter()
                    .prepare(input, Product.Provider.KUTXABANK))
        .isInstanceOf(IllegalArgumentException.class);
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).containsExactly(input);
    }
  }

  @Test
  void rejectsProviderMismatch() throws Exception {
    var input = fixture("01/09/2026");
    assertThatThrownBy(
            () -> new NativeXlsStatementPreparationAdapter().prepare(input, Product.Provider.ING))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsSuppliedWorkbookWithoutPublishingPrivateContent() throws Exception {
    String sample = System.getProperty("kutxabank.sample");
    assumeTrue(sample != null && Files.isRegularFile(Path.of(sample)));
    Path input = dir.resolve("sample.xls");
    Files.copy(Path.of(sample), input);
    var adapter = new NativeXlsStatementPreparationAdapter();
    var output = adapter.prepare(input, Product.Provider.KUTXABANK);
    adapter.validate(output);
    assertThat(Files.readAllLines(output)).hasSize(85);
    assertThat(Files.readString(output))
        .isEqualTo(Files.readString(adapter.prepare(input, Product.Provider.KUTXABANK)));
  }
}
