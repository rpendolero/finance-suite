package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.domain.*;
import com.finance.server.application.port.*;
import com.finance.server.application.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MultiProviderTest {
  @Test
  void paypalAndBankDuplicateCandidatesRemainVisibleUntilReviewed() {
    var ledger = mock(LedgerPort.class);
    var settings = mock(SettingsPort.class);
    var clock = Clock.fixed(Instant.parse("2026-10-02T06:00:00Z"), ZoneOffset.UTC);
    when(ledger.products())
        .thenReturn(
            List.of(
                new Product(
                    "bank",
                    "ING",
                    Product.ProductType.ACCOUNT,
                    "EUR",
                    new BigDecimal("1000.00"),
                    clock.instant(),
                    null,
                    null,
                    Product.Provider.ING),
                new Product(
                    "paypal",
                    "PayPal",
                    Product.ProductType.WALLET,
                    "EUR",
                    new BigDecimal("50.00"),
                    clock.instant(),
                    "bank",
                    null,
                    Product.Provider.PAYPAL)));
    var p =
        new com.finance.domain.Period(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));
    var bank =
        new Movement(
            "b",
            "bank",
            "b",
            LocalDate.parse("2026-09-10"),
            new BigDecimal("-25.00"),
            "EUR",
            "PayPal Shop",
            "Shop",
            "SHOPPING",
            Movement.Kind.NORMAL,
            Movement.Status.BOOKED);
    var paypal =
        new Movement(
            "p",
            "paypal",
            "p",
            LocalDate.parse("2026-09-11"),
            new BigDecimal("-25.00"),
            "EUR",
            "Shop",
            "Shop",
            "SHOPPING",
            Movement.Kind.NORMAL,
            Movement.Status.BOOKED);
    when(ledger.movements(p, null)).thenReturn(List.of(bank, paypal));
    var queries = new FinanceService(ledger, clock);
    assertThat(queries.providerSummary(p, Product.Provider.ING).expenses())
        .isEqualByComparingTo("25");
    assertThat(queries.providerSummary(p, Product.Provider.PAYPAL).expenses())
        .isEqualByComparingTo("25");
    assertThat(new ExtendedAnalysisService(queries, ledger, settings).reconciliation(p)).hasSize(1);
    assertThat(queries.forecast(p, 30).accountBalances()).isEqualByComparingTo("1050");
  }
}
