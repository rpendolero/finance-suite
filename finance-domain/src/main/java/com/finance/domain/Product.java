package com.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Product(
    String id,
    String name,
    ProductType type,
    String currency,
    BigDecimal balance,
    Instant balanceAt,
    String linkedAccountId,
    BigDecimal creditLimit,
    Provider provider) {
  public enum ProductType {
    ACCOUNT,
    DEBIT_CARD,
    CREDIT_CARD,
    WALLET
  }

  public enum Provider {
    KUTXABANK,
    ING,
    PAYPAL
  }

  public Product(
      String id,
      String name,
      ProductType type,
      String currency,
      BigDecimal balance,
      Instant balanceAt,
      String linkedAccountId,
      BigDecimal creditLimit) {
    this(
        id,
        name,
        type,
        currency,
        balance,
        balanceAt,
        linkedAccountId,
        creditLimit,
        Provider.KUTXABANK);
  }

  public Product {
    if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}"))
      throw new IllegalArgumentException("Identificador de producto inválido");
    if (!"EUR".equals(currency))
      throw new IllegalArgumentException("Esta versión analiza solo EUR");
    if (balance == null || balanceAt == null || type == null || name == null || provider == null)
      throw new IllegalArgumentException("Producto incompleto");
    if ((type == ProductType.ACCOUNT || type == ProductType.WALLET) && creditLimit != null)
      throw new IllegalArgumentException("Una cuenta o monedero no tiene límite de tarjeta");
  }

  public boolean liquid() {
    return type == ProductType.ACCOUNT || type == ProductType.WALLET;
  }
}
