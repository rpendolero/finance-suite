package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.application.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClassificationManagementServiceTest {

  LedgerPort ledger = mock(LedgerPort.class);
  SettingsPort settings = mock(SettingsPort.class);
  MerchantNormalizationService merchants = new MerchantNormalizationService();
  CategoryCatalogService categories = new CategoryCatalogService();
  MovementClassificationService classifier =
      new MovementClassificationService(
          merchants, categories, new MovementKindDetectionService());
  ClassificationManagementService service =
      new ClassificationManagementService(
          ledger, settings, classifier, categories, merchants);

  @Test
  void manualClassificationIsPersistedWithMaximumConfidence() {
    Movement movement = unclassified("MERCADONA 1234");
    when(ledger.movement("id")).thenReturn(Optional.of(movement));

    var result =
        service.classifyManually(
            "id",
            "ALIMENTACION",
            "SUPERMERCADO",
            Movement.Kind.NORMAL,
            false,
            false);

    assertThat(result.movement().classificationSource())
        .isEqualTo(Movement.ClassificationSource.MANUAL);
    assertThat(result.movement().classificationConfidence()).isEqualByComparingTo("1.0000");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Movement>> captor = ArgumentCaptor.forClass(List.class);
    verify(ledger).updateClassifications(captor.capture());
    assertThat(captor.getValue()).singleElement().extracting(Movement::category)
        .isEqualTo("ALIMENTACION");
  }

  @Test
  void paypalSettlementCanBeMarkedAsNonComputable() {
    Movement movement = unclassified("CARGO PAYPAL EUROPE");
    when(ledger.movement("id")).thenReturn(Optional.of(movement));

    var result =
        service.classifyManually(
            "id",
            "NO_COMPUTABLE",
            "LIQUIDACION_PAYPAL",
            Movement.Kind.WALLET_SETTLEMENT,
            false,
            false);

    assertThat(result.movement().kind()).isEqualTo(Movement.Kind.WALLET_SETTLEMENT);
    assertThat(result.movement().subcategory()).isEqualTo("LIQUIDACION_PAYPAL");
    assertThat(result.movement().included()).isFalse();
  }

  @Test
  void merchantRuleCanBeCreatedAndAppliedHistorically() {
    Movement movement = unclassified("MERCADONA 1234");
    when(ledger.movement("id")).thenReturn(Optional.of(movement));
    when(ledger.allMovements()).thenReturn(List.of(movement));
    when(settings.rules()).thenAnswer(
        invocation ->
            List.of(
                new ClassificationRule(
                    "merchant_mercadona_test",
                    10,
                    ClassificationRule.MatchType.MERCHANT,
                    "MERCADONA",
                    "ALIMENTACION",
                    "SUPERMERCADO",
                    Movement.Kind.NORMAL,
                    new BigDecimal("0.9900"))));

    var result =
        service.classifyManually(
            "id",
            "ALIMENTACION",
            "SUPERMERCADO",
            Movement.Kind.NORMAL,
            true,
            true);

    verify(settings).saveRule(any(ClassificationRule.class));
    assertThat(result.reclassified()).isEqualTo(1);
    verify(ledger, atLeast(2)).updateClassifications(anyList());
  }

  @Test
  void subcategoryDeterminesExcludedKindEvenWhenClientSendsNormal() {
    when(ledger.movement("id")).thenReturn(Optional.of(unclassified("Cargo")));
    var result = service.classifyManually("id", "NO_COMPUTABLE", "MOVIMIENTO_DUPLICADO",
        Movement.Kind.NORMAL, false, false);
    assertThat(result.movement().kind()).isEqualTo(Movement.Kind.DUPLICATE);
    assertThat(result.movement().included()).isFalse();
  }

  @Test
  void normalCategoryRestoresComputability() {
    when(ledger.movement("id")).thenReturn(Optional.of(unclassified("Compra")));
    var result = service.classifyManually("id", "ALIMENTACION", "SUPERMERCADO",
        Movement.Kind.CARD_SETTLEMENT, false, false);
    assertThat(result.movement().kind()).isEqualTo(Movement.Kind.NORMAL);
    assertThat(result.movement().included()).isTrue();
  }

  private Movement unclassified(String description) {
    return new Movement(
        "id",
        "account",
        "external",
        LocalDate.of(2026, 10, 1),
        new BigDecimal("-20.00"),
        "EUR",
        description,
        null,
        "UNCLASSIFIED",
        Movement.Kind.NORMAL,
        Movement.Status.BOOKED);
  }
}
