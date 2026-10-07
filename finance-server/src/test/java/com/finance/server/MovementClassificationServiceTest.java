package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import com.finance.server.application.service.MovementClassificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementClassificationServiceTest {

  private final MovementClassificationService classifier = new MovementClassificationService();

  @Test
  void merchantRuleNormalizesAndClassifiesNoisyBankText() {
    Movement movement =
        movement("COMPRA EN MERCADONA 1234 MADRID ES", null, "UNCLASSIFIED");

    ClassificationRule rule =
        new ClassificationRule(
            "mercadona",
            10,
            ClassificationRule.MatchType.MERCHANT,
            "MERCADONA",
            "ALIMENTACION",
            "SUPERMERCADO",
            Movement.Kind.NORMAL,
            new BigDecimal("0.9900"));

    Movement result = classifier.classify(movement, List.of(rule));

    assertThat(result.normalizedMerchant()).isEqualTo("MERCADONA");
    assertThat(result.category()).isEqualTo("ALIMENTACION");
    assertThat(result.subcategory()).isEqualTo("SUPERMERCADO");
    assertThat(result.classificationSource())
        .isEqualTo(Movement.ClassificationSource.MERCHANT_RULE);
    assertThat(result.classificationConfidence()).isEqualByComparingTo("0.9900");
  }

  @Test
  void manualClassificationIsNeverOverwrittenByRules() {
    Movement manual =
        movement("Compra", "Mercadona", "OCIO")
            .withClassification(
                "OCIO",
                "OTROS_OCIO",
                Movement.Kind.NORMAL,
                Movement.ClassificationSource.MANUAL,
                BigDecimal.ONE.setScale(4));

    ClassificationRule rule =
        new ClassificationRule(
            "mercadona", 10, "MERCADONA", "ALIMENTACION", Movement.Kind.NORMAL);

    Movement result = classifier.classify(manual, List.of(rule));

    assertThat(result.category()).isEqualTo("OCIO");
    assertThat(result.subcategory()).isEqualTo("OTROS_OCIO");
    assertThat(result.classificationSource())
        .isEqualTo(Movement.ClassificationSource.MANUAL);
  }

  @Test
  void cardSettlementIsExcludedFromSpendingWhenDetected() {
    Movement result =
        classifier.classify(
            movement("LIQUIDACION TARJETA DE CREDITO", null, "UNCLASSIFIED"),
            List.of());

    assertThat(result.kind()).isEqualTo(Movement.Kind.CARD_SETTLEMENT);
    assertThat(result.category()).isEqualTo("TRANSFERENCIAS");
    assertThat(result.subcategory()).isEqualTo("LIQUIDACION_TARJETA");
    assertThat(result.included()).isFalse();
  }

  @Test
  void walletSettlementIsExcludedFromDashboardMetrics() {
    Movement movement =
        new Movement(
            "id",
            "account",
            "wallet-settlement",
            LocalDate.of(2026, 10, 1),
            new BigDecimal("-75.00"),
            "EUR",
            "Cargo PayPal",
            "PAYPAL",
            "UNCLASSIFIED",
            Movement.Kind.WALLET_SETTLEMENT,
            Movement.Status.BOOKED);

    Movement result = classifier.classify(movement, List.of());

    assertThat(result.kind()).isEqualTo(Movement.Kind.WALLET_SETTLEMENT);
    assertThat(result.category()).isEqualTo("TRANSFERENCIAS");
    assertThat(result.subcategory()).isEqualTo("LIQUIDACION_MONEDERO");
    assertThat(result.included()).isFalse();
  }

  @Test
  void legacyFoodCategoryIsCanonicalized() {
    Movement result = classifier.classify(movement("Compra", "Tienda", "FOOD"), List.of());

    assertThat(result.category()).isEqualTo("ALIMENTACION");
    assertThat(result.classificationSource())
        .isEqualTo(Movement.ClassificationSource.AUTOMATIC);
  }

  private Movement movement(String description, String merchant, String category) {
    return new Movement(
        "id",
        "account",
        "external",
        LocalDate.of(2026, 10, 1),
        new BigDecimal("-10.00"),
        "EUR",
        description,
        merchant,
        category,
        Movement.Kind.NORMAL,
        Movement.Status.BOOKED);
  }
}
