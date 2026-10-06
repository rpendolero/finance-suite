package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.domain.*;
import com.finance.domain.Period;
import com.finance.server.application.port.*;
import com.finance.server.application.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class FinanceServiceTest {
  LedgerPort ledger = mock(LedgerPort.class);
  Clock clock = Clock.fixed(Instant.parse("2026-10-02T06:00:00Z"), ZoneOffset.UTC);
  FinanceService service = new FinanceService(ledger, clock);
  Period p = new Period(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

  Movement m(
      String id,
      String date,
      String amount,
      String merchant,
      Movement.Kind kind,
      Movement.Status status) {
    return new Movement(
        id,
        "a",
        id,
        LocalDate.parse(date),
        new BigDecimal(amount),
        "EUR",
        merchant,
        merchant,
        "FOOD",
        kind,
        status);
  }

  @Test
  void purchasesAreCountedOnceAndRefundsReduceExpenses() {
    when(ledger.movements(p, null))
        .thenReturn(
            List.of(
                m(
                    "salary",
                    "2026-09-01",
                    "3000.00",
                    "salary",
                    Movement.Kind.NORMAL,
                    Movement.Status.BOOKED),
                m(
                    "purchase",
                    "2026-09-10",
                    "-100.00",
                    "shop",
                    Movement.Kind.NORMAL,
                    Movement.Status.BOOKED),
                m(
                    "refund",
                    "2026-09-11",
                    "20.00",
                    "shop",
                    Movement.Kind.REFUND,
                    Movement.Status.BOOKED),
                m(
                    "settlement",
                    "2026-09-25",
                    "-100.00",
                    "card",
                    Movement.Kind.CARD_SETTLEMENT,
                    Movement.Status.BOOKED),
                m(
                    "transfer",
                    "2026-09-26",
                    "-500.00",
                    "own",
                    Movement.Kind.INTERNAL_TRANSFER,
                    Movement.Status.BOOKED),
                m(
                    "pending",
                    "2026-09-27",
                    "-50.00",
                    "shop",
                    Movement.Kind.NORMAL,
                    Movement.Status.PENDING)));
    var s = service.summary(p, null);
    assertThat(s.income()).isEqualByComparingTo("3000");
    assertThat(s.expenses()).isEqualByComparingTo("80");
    assertThat(s.net()).isEqualByComparingTo("2920");
    assertThat(s.excluded()).isEqualTo(3);
    assertThat(s.pending()).isEqualTo(1);
    assertThat(s.savingsRatePercent()).isEqualByComparingTo("97.33");
  }

  @Test
  void zeroIncomeHasNoMisleadingSavingsRate() {
    when(ledger.movements(p, null)).thenReturn(List.of());
    assertThat(service.summary(p, null).savingsRatePercent()).isNull();
  }

  @Test
  void detectsMonthlyRecurrenceAndIncrease() {
    var history = new Period(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-09-30"));
    when(ledger.movements(history, null))
        .thenReturn(
            List.of(
                m(
                    "1",
                    "2026-07-03",
                    "-50.00",
                    "energy",
                    Movement.Kind.NORMAL,
                    Movement.Status.BOOKED),
                m(
                    "2",
                    "2026-08-03",
                    "-50.00",
                    "energy",
                    Movement.Kind.NORMAL,
                    Movement.Status.BOOKED),
                m(
                    "3",
                    "2026-09-03",
                    "-60.00",
                    "energy",
                    Movement.Kind.NORMAL,
                    Movement.Status.BOOKED)));
    var recurring = service.recurring(history);
    assertThat(recurring).hasSize(1);
    assertThat(recurring.get(0).estimatedNextDate()).isEqualTo(LocalDate.parse("2026-10-03"));
    var analysis = new ExtendedAnalysisService(service, ledger, mock(SettingsPort.class));
    assertThat(analysis.increases(history).get(0).changePercent()).isEqualByComparingTo("20");
  }

  @Test
  void rejectsExcessiveQueryRangeAndPage() {
    assertThatThrownBy(
            () -> new Period(LocalDate.parse("2020-01-01"), LocalDate.parse("2026-01-01")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.movements(p, null, 0, 501))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cashForecastUsesAccountFlowsAndDoesNotSubtractCreditPurchasesTwice() {
    when(ledger.products())
        .thenReturn(
            List.of(
                new Product(
                    "a",
                    "Account",
                    Product.ProductType.ACCOUNT,
                    "EUR",
                    new BigDecimal("1000.00"),
                    clock.instant(),
                    null,
                    null),
                new Product(
                    "card",
                    "Card",
                    Product.ProductType.CREDIT_CARD,
                    "EUR",
                    new BigDecimal("-100.00"),
                    clock.instant(),
                    "a",
                    new BigDecimal("2000.00"))));
    var account =
        m(
            "payment",
            "2026-09-10",
            "-100.00",
            "card",
            Movement.Kind.CARD_SETTLEMENT,
            Movement.Status.BOOKED);
    var card =
        new Movement(
            "purchase",
            "card",
            "purchase",
            LocalDate.parse("2026-09-05"),
            new BigDecimal("-100.00"),
            "EUR",
            "shop",
            "shop",
            "FOOD",
            Movement.Kind.NORMAL,
            Movement.Status.BOOKED);
    when(ledger.movements(p, null)).thenReturn(List.of(account, card));
    assertThat(service.forecast(p, 30).estimatedAccountBalance()).isEqualByComparingTo("900");
    assertThat(service.cards().get(0).utilizationPercent()).isEqualByComparingTo("5");
  }
}
