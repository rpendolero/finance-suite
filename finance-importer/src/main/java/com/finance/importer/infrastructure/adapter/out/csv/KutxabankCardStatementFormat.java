package com.finance.importer.infrastructure.adapter.out.csv;

import com.finance.domain.Product.Provider;
import java.util.List;

public final class KutxabankCardStatementFormat implements NativeStatementFormat {
  public Provider provider() {
    return Provider.KUTXABANK;
  }

  public String sheetName() {
    return "Listado";
  }

  public List<String> headers() {
    return List.of("fecha", "concepto", "fecha valor", "importe de la operación");
  }

  public int amountColumn() {
    return 3;
  }

  public Integer balanceColumn() {
    return null;
  }

  public int descriptionColumn() {
    return 1;
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
}
