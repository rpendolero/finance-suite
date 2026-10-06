package com.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Movement(
    String id,
    String productId,
    String externalId,
    LocalDate date,
    BigDecimal amount,
    String currency,
    String description,
    String merchant,
    String category,
    Kind kind,
    Status status) {
  public enum Kind {
    NORMAL,
    REFUND,
    INTERNAL_TRANSFER,
    CARD_SETTLEMENT,
    DUPLICATE
  }

  public enum Status {
    BOOKED,
    PENDING
  }

  public Movement {
    if (amount == null || amount.scale() > 2)
      throw new IllegalArgumentException("Importe con máximo dos decimales");
    if (!"EUR".equals(currency)) throw new IllegalArgumentException("Moneda no soportada");
    if (kind == Kind.REFUND && amount.signum() <= 0)
      throw new IllegalArgumentException("Devolución debe ser positiva");
    if (externalId != null && externalId.length() > 160
        || description != null && description.length() > 1000
        || merchant != null && merchant.length() > 200
        || category != null && category.length() > 64)
      throw new IllegalArgumentException("Texto de movimiento demasiado largo");
    if (date == null
        || kind == null
        || status == null
        || externalId == null
        || externalId.isBlank()
        || description == null
        || category == null) throw new IllegalArgumentException("Movimiento incompleto");
  }

  public boolean included() {
    return status == Status.BOOKED && (kind == Kind.NORMAL || kind == Kind.REFUND);
  }
}
