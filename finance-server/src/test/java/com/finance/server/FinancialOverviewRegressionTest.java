package com.finance.server;

import com.finance.domain.*;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialOverviewRegressionTest {
  private final LedgerPort ledger = mock(LedgerPort.class);
  private final LocalDate today = LocalDate.of(2026, 11, 9);
  private final Clock clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
  private final Period month = new Period(today.withDayOfMonth(1), today.withDayOfMonth(30));
  private final DashboardAnalysisService dashboard = new DashboardAnalysisService(ledger);
  private final FinancialSummaryService summary = new FinancialSummaryService(ledger);
  private final LiquidityForecastService forecasts = new LiquidityForecastService(ledger, clock);

  @Test void mixedCurrenciesCannotBeReportedAsEurosOrSilentlyOmittedFromForecast() {
    setup(List.of(m("salary", "2000", "EUR", Movement.Kind.NORMAL),
        m("foreign", "-100", "USD", Movement.Kind.NORMAL)));
    for (Runnable query : List.<Runnable>of(
        () -> dashboard.overview(month, null), () -> dashboard.categories(month, null),
        () -> dashboard.trend(month, null, "DAY"), () -> dashboard.calendar(month, null),
        () -> dashboard.flow(month, null, DashboardAnalysisService.FlowDirection.EXPENSE, 0, 25),
        () -> dashboard.products(month), () -> summary.summary(month, null), () -> forecasts.forecast(month, 30))) {
      assertThatThrownBy(query::run).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EUR");
    }
  }

  @Test void currentMonthForecastUsesNineObservedDaysRatherThanThirtyCalendarDays() {
    setup(List.of(m("salary", "2000", "EUR", Movement.Kind.NORMAL),
        m("purchase", "-100", "EUR", Movement.Kind.NORMAL)));
    var f = forecasts.forecast(month, 30);
    assertThat(f.estimatedIncome()).isEqualByComparingTo("6666.67");
    assertThat(f.estimatedExpenses()).isEqualByComparingTo("333.33");
    assertThat(f.estimatedNetFlow()).isEqualByComparingTo("6333.34");
    assertThat(f.estimatedAccountBalance()).isEqualByComparingTo("8233.34");
  }

  @Test void historyWhollyInFutureIsUnavailableRatherThanAZeroProjection() {
    setup(List.of());
    assertThatThrownBy(() -> forecasts.forecast(new Period(today.plusDays(1), today.plusDays(10)), 30))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("histórico");
  }

  @Test void ownTransferAndCardSettlementDoNotInflateAnyPeriodView() {
    setup(List.of(m("salary", "2000", "EUR", Movement.Kind.NORMAL),
        m("transfer-out", "-300", "EUR", Movement.Kind.INTERNAL_TRANSFER),
        m("transfer-in", "300", "EUR", Movement.Kind.INTERNAL_TRANSFER),
        m("purchase", "-100", "EUR", Movement.Kind.NORMAL),
        m("settlement-out", "-100", "EUR", Movement.Kind.CARD_SETTLEMENT),
        m("settlement-in", "100", "EUR", Movement.Kind.CARD_SETTLEMENT)));
    when(ledger.products()).thenReturn(List.of(
        new Product("account", "Cuenta diaria", Product.ProductType.ACCOUNT, "EUR", new BigDecimal("1600"), clock.instant(), null, null),
        new Product("reserve", "Cuenta reserva", Product.ProductType.ACCOUNT, "EUR", new BigDecimal("300"), clock.instant(), null, null),
        new Product("card", "Tarjeta liquidada", Product.ProductType.CREDIT_CARD, "EUR", BigDecimal.ZERO, clock.instant(), "account", new BigDecimal("2000"))));
    var o = dashboard.overview(month, null);
    assertThat(o.income()).isEqualByComparingTo("2000");
    assertThat(o.expenses()).isEqualByComparingTo("100");
    assertThat(o.availableAfterAllocations()).isEqualByComparingTo("1900");
    assertThat(o.totalBalance()).isEqualByComparingTo("1900");
    assertThat(summary.summary(month, null).net()).isEqualByComparingTo("1900");
    assertThat(dashboard.flow(month, null, DashboardAnalysisService.FlowDirection.EXPENSE, 0, 25).total()).isEqualByComparingTo("100");
    assertThat(dashboard.calendar(month, null).stream().map(DashboardAnalysisService.CalendarDay::net).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1900");
  }

  @Test void refundsRemainIncomeAndExcludedForeignTransfersDoNotInvalidateResult() {
    setup(List.of(m("salary", "2000", "EUR", Movement.Kind.NORMAL), m("purchase", "-100", "EUR", Movement.Kind.NORMAL),
        m("refund", "20", "EUR", Movement.Kind.REFUND), m("excluded", "999", "USD", Movement.Kind.INTERNAL_TRANSFER)));
    assertThat(dashboard.overview(month, null).income()).isEqualByComparingTo("2020");
    assertThat(dashboard.overview(month, null).expenses()).isEqualByComparingTo("100");
    assertThat(dashboard.overview(month, null).availableAfterAllocations()).isEqualByComparingTo("1920");
  }

  @Test void realizedMonthlyIncomeIsNotAlsoProjectedInsideFutureWindow() {
    var history = new Period(LocalDate.of(2026, 9, 1), month.to());
    setup(List.of());
    when(ledger.movements(history, null)).thenReturn(List.of(
        dated("s1", LocalDate.of(2026, 9, 2), "2000"), dated("s2", LocalDate.of(2026, 10, 2), "2000"),
        dated("s3", LocalDate.of(2026, 11, 2), "2000")));
    var f = forecasts.forecast(history, 30);
    assertThat(f.recurringPatterns()).isEqualTo(1);
    assertThat(f.recurringIncome()).isEqualByComparingTo("2000"); // only December 2, not November 2 again
    assertThat(f.estimatedNetFlow()).isEqualByComparingTo("2000");
  }

  private void setup(List<Movement> rows) {
    when(ledger.products()).thenReturn(List.of(new Product("account", "Cuenta sintética", Product.ProductType.ACCOUNT,
        "EUR", new BigDecimal("1900"), clock.instant(), null, null)));
    when(ledger.movements(month, null)).thenReturn(rows);
  }
  private Movement dated(String id, LocalDate date, String amount) {
    return new Movement(id, "account", id, date, new BigDecimal(amount), "EUR", "Nómina", "Nómina", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED);
  }
  private Movement m(String id, String amount, String currency, Movement.Kind kind) {
    return new Movement(id, id.equals("purchase") ? "card" : "account", id, today, new BigDecimal(amount), currency, id, id,
        kind == Movement.Kind.REFUND ? "DEVOLUCIONES" : "UNCLASSIFIED", kind, Movement.Status.BOOKED);
  }
}
