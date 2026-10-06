package com.finance.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Period(LocalDate from, LocalDate to) {
  public Period {
    if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 1096)
      throw new IllegalArgumentException("Periodo inválido: máximo tres años");
  }
}
