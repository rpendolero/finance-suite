package com.finance.importer.infrastructure.adapter.out.csv;

import com.finance.domain.Product.Provider;
import java.util.List;

/** Account XLS layout verified against the supplied Kutxabank export. */
public final class KutxabankAccountStatementFormat implements NativeStatementFormat {
  public Provider provider() {
    return Provider.KUTXABANK;
  }

  public String sheetName() {
    return "Listado";
  }

  public List<String> headers() {
    return List.of("fecha", "concepto", "fecha valor", "importe", "saldo");
  }

  public int amountColumn() {
    return 3;
  }

  public Integer balanceColumn() {
    return 4;
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
    return "kutxabank-";
  }
}
