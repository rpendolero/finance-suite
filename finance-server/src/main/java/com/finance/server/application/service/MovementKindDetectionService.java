package com.finance.server.application.service;

import com.finance.domain.Movement;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** Conservative fallback detector for movements that must not be counted as spending twice. */
public final class MovementKindDetectionService {

  public record Detection(
      Movement.Kind kind, String category, String subcategory, BigDecimal confidence) {}

  public Optional<Detection> detect(Movement movement) {
    String text = normalize(movement.description() + " " + nullSafe(movement.merchant()));

    if (containsAny(
        text,
        "TRASPASO ENTRE CUENTAS",
        "TRANSFERENCIA ENTRE CUENTAS",
        "TRASPASO A MI CUENTA",
        "TRASPASO DE MI CUENTA")) {
      return Optional.of(
          new Detection(
              Movement.Kind.INTERNAL_TRANSFER,
              CategoryCatalogService.NON_COMPUTABLE,
              "TRASPASO_INTERNO",
              new BigDecimal("0.9800")));
    }

    if (containsAny(
        text,
        "LIQUIDACION TARJETA",
        "LIQUIDACION DE TARJETA",
        "PAGO MENSUAL TARJETA",
        "PAGO TARJETA DE CREDITO",
        "RECIBO TARJETA DE CREDITO")) {
      return Optional.of(
          new Detection(
              Movement.Kind.CARD_SETTLEMENT,
              CategoryCatalogService.NON_COMPUTABLE,
              "LIQUIDACION_TARJETA",
              new BigDecimal("0.9800")));
    }

    return Optional.empty();
  }

  private boolean containsAny(String text, String... candidates) {
    for (String candidate : candidates) if (text.contains(candidate)) return true;
    return false;
  }

  private String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
