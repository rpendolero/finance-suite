package com.finance.statements;

import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public final class IngCreditCardStatementFormat implements NativeStatementFormat {
  public String sheetName() {
    return "Tarjetas";
  }

  public List<String> headers() {
    return List.of(
        "FECHA VALOR",
        "CATEGORÍA",
        "SUBCATEGORÍA",
        "DESCRIPCION",
        "COMENTARIO",
        "",
        "ESTADO",
        "IMPORTE (€)");
  }

  public int amountColumn() {
    return 7;
  }

  public Integer balanceColumn() {
    return null;
  }

  public String identifierPrefix() {
    return "ing-card-";
  }

  public String normalizeDescription(String text) {
    return text.trim().replaceAll("\\s+", " ");
  }

  public void validateTitle(Sheet sheet, DataFormatter formatter) {
    if (sheet.getRow(0) == null
        || !"Tarjeta Crédito".equals(formatter.formatCellValue(sheet.getRow(0).getCell(0)).trim()))
      throw new IllegalArgumentException("Solo se ha verificado la tarjeta de crédito ING");
  }

  public String movementStatus(Row row, DataFormatter formatter) {
    String value = formatter.formatCellValue(row.getCell(6)).trim();
    if ("Liquidado".equalsIgnoreCase(value)) return "BOOKED";
    throw new IllegalArgumentException("Estado ING no verificado: " + value);
  }
}
