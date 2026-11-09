package com.finance.statements;

import com.finance.domain.Product.Provider;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

/** Kutxabank's Consulta de extractos export, distinct from card movements. */
public final class KutxabankCardExtractStatementFormat implements NativeStatementFormat {
  private static final Set<String> SUMMARIES =
      Set.of("saldo anterior", "total dispuesto", "total disponible");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  public Provider provider() {
    return Provider.KUTXABANK;
  }

  public String sheetName() {
    return "Listado";
  }

  public List<String> headers() {
    return List.of("fecha de operación", "fecha de proceso", "concepto", "importe", "tarjeta");
  }

  public int descriptionColumn() {
    return 2;
  }

  public int amountColumn() {
    return 3;
  }

  public Integer balanceColumn() {
    return null;
  }

  public Integer categoryColumn() {
    return null;
  }

  public boolean textDates() {
    return true;
  }

  public String identifierPrefix() {
    return "kutxabank-card-";
  }

  public boolean isSummaryRow(Row row, DataFormatter formatter) {
    return formatter.formatCellValue(row.getCell(0)).isBlank()
        && formatter.formatCellValue(row.getCell(1)).isBlank()
        && formatter.formatCellValue(row.getCell(4)).isBlank()
        && SUMMARIES.contains(
            formatter.formatCellValue(row.getCell(2)).trim().toLowerCase(Locale.ROOT));
  }

  public String identifierContext(Row row, DataFormatter formatter) {
    LocalDate processed = LocalDate.parse(formatter.formatCellValue(row.getCell(1)).trim(), DATE);
    String card = formatter.formatCellValue(row.getCell(4)).trim();
    if (card.isBlank()) throw new IllegalArgumentException("Tarjeta requerida");
    return processed + "\u001f" + card;
  }

  public String movementKind(Row row, DataFormatter formatter) {
    return "PAGO RECIBO".equalsIgnoreCase(formatter.formatCellValue(row.getCell(2)).trim())
        ? "CARD_SETTLEMENT" : "NORMAL";
  }
}
