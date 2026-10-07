package com.finance.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record ClassificationRule(
    String id,
    int priority,
    MatchType matchType,
    String contains,
    String category,
    String subcategory,
    Movement.Kind kind,
    BigDecimal confidence) {

  public enum MatchType {
    MERCHANT,
    CONTAINS,
    REGEX
  }

  /** Backward-compatible rule constructor. */
  public ClassificationRule(
      String id, int priority, String contains, String category, Movement.Kind kind) {
    this(
        id,
        priority,
        MatchType.CONTAINS,
        contains,
        category,
        null,
        kind,
        new BigDecimal("0.9000"));
  }

  public ClassificationRule {
    if (matchType == null) matchType = MatchType.CONTAINS;
    if (confidence == null) confidence = new BigDecimal("0.9000");

    if (id == null
        || !id.matches("[a-zA-Z0-9_-]{1,64}")
        || contains == null
        || contains.isBlank()
        || contains.length() > 200
        || category == null
        || category.isBlank()
        || category.length() > 64
        || subcategory != null && subcategory.length() > 64
        || kind == null
        || confidence.compareTo(BigDecimal.ZERO) < 0
        || confidence.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Regla inválida");

    if (matchType == MatchType.REGEX) {
      try {
        Pattern.compile(contains, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      } catch (PatternSyntaxException ex) {
        throw new IllegalArgumentException("Expresión regular inválida", ex);
      }
    }
  }

  public boolean matches(Movement movement) {
    String description = movement.description() == null ? "" : movement.description();
    String merchant = movement.merchant() == null ? "" : movement.merchant();
    String normalized =
        movement.normalizedMerchant() == null ? "" : movement.normalizedMerchant();

    String text = (description + " " + merchant + " " + normalized).toUpperCase(Locale.ROOT);
    String expression = contains.toUpperCase(Locale.ROOT);

    return switch (matchType) {
      case MERCHANT -> !normalized.isBlank() && normalized.toUpperCase(Locale.ROOT).contains(expression);
      case CONTAINS -> text.contains(expression);
      case REGEX ->
          Pattern.compile(contains, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
              .matcher(text)
              .find();
    };
  }
}
