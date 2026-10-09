package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import com.finance.server.application.port.CategoryCatalogPort;
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
  CategoryCatalogPort catalog = mock(CategoryCatalogPort.class);
  CategoryCatalogService categories = new CategoryCatalogService(catalog);

  {
    when(catalog.findActiveByCode(anyString())).thenAnswer(invocation -> {
      String code = invocation.getArgument(0);
      return switch (code) {
        case "ALIMENTACION" -> Optional.of(new CategoryCatalogPort.Category(
            "ALIMENTACION", "Alimentación",
            List.of(new CategoryCatalogPort.Subcategory("SUPERMERCADO", "Supermercado"))));
        case "NO_COMPUTABLE" -> Optional.of(new CategoryCatalogPort.Category(
            "NO_COMPUTABLE", "No computable",
            List.of(new CategoryCatalogPort.Subcategory("LIQUIDACION_PAYPAL", "Liquidación PayPal"), new CategoryCatalogPort.Subcategory("MOVIMIENTO_DUPLICADO", "Movimiento duplicado"))));
        default -> Optional.empty();
      };
    });
  }
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
    verify(ledger).updateClassifications(anyList());
  }

  @Test
  void applyingToMerchantChangesOnlyMatchingHistoryIncludingManualRowsAndKeepsRefunds() {
    var selected = unclassified("MERCADONA 1234");
    var manual = new Movement("manual", "other-account", "manual", LocalDate.of(2026, 9, 1),
        new BigDecimal("-40"), "EUR", "Compra Mercadona", "MERCADONA", "UNCLASSIFIED", Movement.Kind.NORMAL, Movement.Status.BOOKED)
        .withClassification("UNCLASSIFIED", null, Movement.Kind.NORMAL, Movement.ClassificationSource.MANUAL, BigDecimal.ONE);
    var refund = new Movement("refund", "account", "refund", LocalDate.of(2026, 8, 1),
        new BigDecimal("10"), "EUR", "Devolución Mercadona", "MERCADONA", "UNCLASSIFIED", Movement.Kind.REFUND, Movement.Status.BOOKED);
    var unrelated = new Movement("other", "account", "other", LocalDate.of(2026, 10, 1),
        new BigDecimal("-200"), "EUR", "Compra Lidl", "LIDL", "UNCLASSIFIED", Movement.Kind.NORMAL, Movement.Status.BOOKED);
    when(ledger.movement("id")).thenReturn(Optional.of(selected));
    when(ledger.allMovements()).thenReturn(List.of(selected, manual, refund, unrelated));
    var result = service.classifyManually("id", "ALIMENTACION", "SUPERMERCADO", Movement.Kind.NORMAL, true, true);
    assertThat(result.reclassified()).isEqualTo(3);
    @SuppressWarnings("unchecked") ArgumentCaptor<List<Movement>> captor = ArgumentCaptor.forClass(List.class);
    verify(ledger).updateClassifications(captor.capture());
    assertThat(captor.getValue()).extracting(Movement::id).containsExactly("id", "manual", "refund");
    assertThat(captor.getValue()).allSatisfy(value -> {
      assertThat(value.category()).isEqualTo("ALIMENTACION");
      assertThat(value.classificationSource()).isEqualTo(Movement.ClassificationSource.MANUAL);
    });
    assertThat(captor.getValue().get(2).kind()).isEqualTo(Movement.Kind.REFUND);
    assertThat(captor.getValue().get(2).amount()).isEqualByComparingTo("10");
    verify(settings).saveRule(any(ClassificationRule.class));
    verify(settings, never()).rules();
  }

  @Test
  void merchantValidationHappensBeforeAnyWrites() {
    var selected = new Movement("id", "account", "id", LocalDate.of(2026, 10, 1),
        new BigDecimal("10"), "EUR", "Mercadona", "MERCADONA", "UNCLASSIFIED", Movement.Kind.NORMAL, Movement.Status.BOOKED);
    when(ledger.movement("id")).thenReturn(Optional.of(selected));
    var debit = new Movement("debit", "account", "debit", LocalDate.of(2026, 9, 1),
        new BigDecimal("-20"), "EUR", "Mercadona", "MERCADONA", "UNCLASSIFIED", Movement.Kind.NORMAL, Movement.Status.BOOKED);
    when(ledger.allMovements()).thenReturn(List.of(selected, debit));
    assertThatThrownBy(() -> service.classifyManually("id", "ALIMENTACION", "SUPERMERCADO", Movement.Kind.REFUND, true, true))
        .isInstanceOf(IllegalArgumentException.class);
    verify(ledger, never()).updateClassifications(anyList());
    verify(settings, never()).saveRule(any(ClassificationRule.class));
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
