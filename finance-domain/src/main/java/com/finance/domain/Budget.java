package com.finance.domain;

import java.math.BigDecimal;

public record Budget(String month, String category, BigDecimal amount) {
  public Budget {
    java.time.YearMonth.parse(month);
    if (category == null
        || category.isBlank()
        || category.length() > 64
        || amount == null
        || amount.signum() < 0
        || amount.scale() > 2) throw new IllegalArgumentException("Presupuesto inválido");
  }
}
