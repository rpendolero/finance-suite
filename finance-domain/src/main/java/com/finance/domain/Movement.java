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
    Status status,
    String normalizedMerchant,
    String subcategory,
    ClassificationSource classificationSource,
    BigDecimal classificationConfidence) {

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

  public enum ClassificationSource {
    MANUAL,
    MERCHANT_RULE,
    PATTERN_RULE,
    AUTOMATIC,
    UNCLASSIFIED
  }

  /**
   * Backward-compatible constructor used by importers and existing tests.
   * Imported categories are considered automatic until the classifier evaluates them.
   */
  public Movement(
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
    this(
        id,
        productId,
        externalId,
        date,
        amount,
        currency,
        description,
        merchant,
        category,
        kind,
        status,
        null,
        null,
        "UNCLASSIFIED".equalsIgnoreCase(category)
            ? ClassificationSource.UNCLASSIFIED
            : ClassificationSource.AUTOMATIC,
        "UNCLASSIFIED".equalsIgnoreCase(category)
            ? BigDecimal.ZERO.setScale(4)
            : new BigDecimal("0.5000"));
  }

  public Movement {
    if (classificationSource == null) {
      classificationSource =
          "UNCLASSIFIED".equalsIgnoreCase(category)
              ? ClassificationSource.UNCLASSIFIED
              : ClassificationSource.AUTOMATIC;
    }
    if (classificationConfidence == null) {
      classificationConfidence =
          classificationSource == ClassificationSource.UNCLASSIFIED
              ? BigDecimal.ZERO.setScale(4)
              : new BigDecimal("0.5000");
    }

    if (amount == null || amount.scale() > 2)
      throw new IllegalArgumentException("Importe con máximo dos decimales");
    if (!"EUR".equals(currency)) throw new IllegalArgumentException("Moneda no soportada");
    if (kind == Kind.REFUND && amount.signum() <= 0)
      throw new IllegalArgumentException("Devolución debe ser positiva");
    if (externalId != null && externalId.length() > 160
        || description != null && description.length() > 1000
        || merchant != null && merchant.length() > 200
        || normalizedMerchant != null && normalizedMerchant.length() > 200
        || category != null && category.length() > 64
        || subcategory != null && subcategory.length() > 64)
      throw new IllegalArgumentException("Texto de movimiento demasiado largo");
    if (date == null
        || kind == null
        || status == null
        || externalId == null
        || externalId.isBlank()
        || description == null
        || category == null)
      throw new IllegalArgumentException("Movimiento incompleto");
    if (classificationConfidence.compareTo(BigDecimal.ZERO) < 0
        || classificationConfidence.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Confianza de clasificación fuera de rango");
  }

  public Movement normalizedMerchantAs(String value) {
    return new Movement(
        id,
        productId,
        externalId,
        date,
        amount,
        currency,
        description,
        merchant,
        category,
        kind,
        status,
        value,
        subcategory,
        classificationSource,
        classificationConfidence);
  }

  public Movement withClassification(
      String newCategory,
      String newSubcategory,
      Kind newKind,
      ClassificationSource source,
      BigDecimal confidence) {
    return new Movement(
        id,
        productId,
        externalId,
        date,
        amount,
        currency,
        description,
        merchant,
        newCategory,
        newKind,
        status,
        normalizedMerchant,
        newSubcategory,
        source,
        confidence);
  }

  public boolean included() {
    return status == Status.BOOKED && (kind == Kind.NORMAL || kind == Kind.REFUND);
  }
}
