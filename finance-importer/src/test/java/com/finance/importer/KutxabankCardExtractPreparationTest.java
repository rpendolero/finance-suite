package com.finance.importer;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.finance.domain.Product;
import com.finance.statements.NativeXlsStatementPreparationAdapter;
import com.finance.statements.StatementFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KutxabankCardExtractPreparationTest {
  @TempDir Path dir;

  @Test
  void importsTransactionsWithoutSummariesAndKeepsOriginalDatesSignsAndInstallments() throws Exception {
    var adapter = new NativeXlsStatementPreparationAdapter();
    var input = fixture(sheet -> {});
    var output = adapter.prepare(input, StatementFormat.KUTXABANK_CARD_XLS);
    assertThat(Files.readString(output)).isEqualTo(
        Files.readString(adapter.prepare(input, Product.Provider.KUTXABANK)));
    var rows = records(output);
    assertThat(rows).hasSize(5);
    assertThat(rows).extracting(row -> row.get("date"))
        .containsExactly("2026-09-01", "2025-06-04", "2026-09-02", "2026-09-02", "2026-09-03");
    assertThat(rows).extracting(row -> row.get("amount"))
        .containsExactly("100.00", "-20.00", "-9.99", "-9.99", "3.00");
    assertThat(rows.get(0).get("kind")).isEqualTo("CARD_SETTLEMENT");
    assertThat(rows.get(1).get("description")).isEqualTo("Compra a plazos (CUOTA 07/12)");
    assertThat(rows.get(2).get("description")).isEqualTo("Compra; ejemplo");
    assertThat(rows).allSatisfy(row -> {
      assertThat(row.get("currency")).isEqualTo("EUR");
      assertThat(row.get("status")).isEqualTo("BOOKED");
    });
    assertThat(rows).extracting(row -> row.get("external_id")).doesNotHaveDuplicates();
    assertThat(rows.get(2).get("external_id")).endsWith("-1");
    assertThat(rows.get(3).get("external_id")).endsWith("-2");
  }

  @Test
  void processingDateAndCardDistinguishOtherwiseIdenticalPurchases() throws Exception {
    var adapter = new NativeXlsStatementPreparationAdapter();
    var initial = records(adapter.prepare(fixture(sheet -> {}), StatementFormat.KUTXABANK_CARD_XLS));
    var differentProcess = records(adapter.prepare(fixture(sheet ->
        sheet.getRow(12).getCell(1).setCellValue("03/09/2026")), StatementFormat.KUTXABANK_CARD_XLS));
    var differentCard = records(adapter.prepare(fixture(sheet ->
        sheet.getRow(12).getCell(4).setCellValue("test-card-2")), StatementFormat.KUTXABANK_CARD_XLS));
    assertThat(differentProcess.get(3).get("external_id"))
        .endsWith("-1").isNotEqualTo(initial.get(2).get("external_id"));
    assertThat(differentCard.get(3).get("external_id"))
        .endsWith("-1").isNotEqualTo(initial.get(2).get("external_id"));
  }

  @Test
  void malformedTransactionsAndUnknownUndatedRowsFailWithoutLeavingPartialOutput() throws Exception {
    List<Consumer<Sheet>> corruptions = List.of(
        sheet -> sheet.getRow(10).getCell(0).setCellValue("31/09/2026"),
        sheet -> sheet.getRow(10).getCell(1).setCellValue("31/09/2026"),
        sheet -> sheet.getRow(10).getCell(4).setCellValue(""),
        sheet -> sheet.getRow(10).getCell(3).setCellValue("not a number"),
        sheet -> sheet.getRow(10).getCell(2).setCellValue(""),
        sheet -> sheet.getRow(14).getCell(2).setCellValue("unknown summary"));
    for (var corruption : corruptions) {
      var input = fixture(corruption);
      assertThatThrownBy(() -> new NativeXlsStatementPreparationAdapter()
          .prepare(input, StatementFormat.KUTXABANK_CARD_XLS))
          .isInstanceOf(IllegalArgumentException.class);
      try (var files = Files.list(dir)) {
        assertThat(files.toList()).containsExactly(input);
      }
    }
  }

  @Test
  void summaryLabelsWithTransactionDatesAreImported() throws Exception {
    var input = fixture(sheet -> sheet.getRow(11).getCell(2).setCellValue("saldo anterior"));
    var rows = records(new NativeXlsStatementPreparationAdapter()
        .prepare(input, StatementFormat.KUTXABANK_CARD_XLS));
    assertThat(rows).hasSize(5);
    assertThat(rows.get(2).get("description")).isEqualTo("saldo anterior");
  }

  @Test
  void rejectsIncompatibleProviderOrProductFormat() throws Exception {
    var input = fixture(sheet -> {});
    var adapter = new NativeXlsStatementPreparationAdapter();
    assertThatThrownBy(() -> adapter.prepare(input, Product.Provider.ING))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.prepare(input, StatementFormat.KUTXABANK_ACCOUNT_XLS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsPrivateExtractSampleWithoutPublishingItsContent() throws Exception {
    String sample = System.getProperty("kutxabank.card.extract.sample");
    assumeTrue(sample != null && Files.isRegularFile(Path.of(sample)));
    var input = dir.resolve("sample.xls");
    Files.copy(Path.of(sample), input);
    var adapter = new NativeXlsStatementPreparationAdapter();
    var output = adapter.prepare(input, StatementFormat.KUTXABANK_CARD_XLS);
    var rows = records(output);
    assertThat(rows).hasSize(75);
    assertThat(rows).extracting(row -> row.get("external_id")).doesNotHaveDuplicates();
    assertThat(rows.stream().filter(row -> row.get("kind").equals("CARD_SETTLEMENT")).count())
        .isEqualTo(1);
    assertThat(Files.readString(output)).isEqualTo(
        Files.readString(adapter.prepare(input, Product.Provider.KUTXABANK)));
  }

  private List<CSVRecord> records(Path csv) throws Exception {
    try (var reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
        var parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader()
            .setSkipHeaderRecord(true).get().parse(reader)) {
      return parser.getRecords();
    }
  }

  private Path fixture(Consumer<Sheet> change) throws Exception {
    Path input = dir.resolve("extract.xls");
    try (var book = new HSSFWorkbook()) {
      var sheet = book.createSheet("Listado");
      sheet.createRow(4).createCell(0).setCellValue("Consulta de extractos");
      var header = sheet.createRow(6);
      String[] columns = {"fecha de operación", "fecha de proceso", "concepto", "importe", "tarjeta"};
      for (int c = 0; c < columns.length; c++) header.createCell(c).setCellValue(columns[c]);
      summary(sheet, 8, "saldo anterior", 200);
      String[] dates = {"01/09/2026", "04/06/2025", "02/09/2026", "02/09/2026", "03/09/2026"};
      String[] descriptions = {"PAGO RECIBO", "Compra a plazos (CUOTA 07/12)",
          "Compra; ejemplo", "Compra; ejemplo", "Abono comercio"};
      double[] amounts = {100, -20, -9.99, -9.99, 3};
      for (int i = 0; i < dates.length; i++) {
        var row = sheet.createRow(i + 9);
        row.createCell(0).setCellValue(dates[i]);
        row.createCell(1).setCellValue(dates[i]);
        row.createCell(2).setCellValue(descriptions[i]);
        row.createCell(3).setCellValue(amounts[i]);
        row.createCell(4).setCellValue("test-card-1");
      }
      summary(sheet, 14, "total dispuesto", 136.98);
      summary(sheet, 15, "total disponible", 863.02);
      change.accept(sheet);
      try (var output = Files.newOutputStream(input)) {
        book.write(output);
      }
    }
    return input;
  }

  private void summary(Sheet sheet, int index, String description, double amount) {
    var row = sheet.createRow(index);
    row.createCell(2).setCellValue(description);
    row.createCell(3).setCellValue(amount);
  }
}
