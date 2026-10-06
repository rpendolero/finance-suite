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
    Provider provider,
    String externalId,
    String maskedPan) {
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

  // Preserve source compatibility with existing clients and configurations.
  public Product(String id, String name, ProductType type, String currency,
      BigDecimal balance, Instant balanceAt, String linkedAccountId,
      BigDecimal creditLimit, Provider provider) {
    this(id, name, type, currency, balance, balanceAt, linkedAccountId,
        creditLimit, provider, null, null);
  }

  public Product {
    if (externalId != null && (externalId.isBlank() || externalId.length() > 160))
      throw new IllegalArgumentException("Referencia bancaria inválida");
    if (maskedPan != null && !maskedPan.matches("\\*{4} [0-9]{4}"))
      throw new IllegalArgumentException("Usa solo el formato **** 1234 para la tarjeta");
    if (maskedPan != null && type != ProductType.DEBIT_CARD && type != ProductType.CREDIT_CARD)
      throw new IllegalArgumentException("Solo una tarjeta puede tener maskedPan");
    if (id != null && id.equals(linkedAccountId))
      throw new IllegalArgumentException("Un producto no puede vincularse a sí mismo");
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
