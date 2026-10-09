package com.finance.statements;

import java.util.List;

public final class IngAccountStatementFormat implements NativeStatementFormat {
  public String sheetName() {
    return "Movimientos";
  }

  public List<String> headers() {
    return List.of(
        "F. VALOR",
        "CATEGORÍA",
        "SUBCATEGORÍA",
        "DESCRIPCIÓN",
        "COMENTARIO",
        "IMPORTE (€)",
        "SALDO (€)");
  }

  public int amountColumn() {
    return 5;
  }

  public Integer balanceColumn() {
    return 6;
  }

  public String identifierPrefix() {
    return "ing-";
  }
}
