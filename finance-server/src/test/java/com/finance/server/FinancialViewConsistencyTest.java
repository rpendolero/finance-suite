package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finance.domain.Movement;
import com.finance.domain.Period;
import com.finance.domain.Product;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.DashboardAnalysisService;
import com.finance.server.application.service.DashboardAnalysisService.FlowDirection;
import com.finance.server.application.service.FinancialSummaryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialViewConsistencyTest {
  private final LocalDate day = LocalDate.of(2026, 10, 1);
  private final Period period = new Period(day, day.plusDays(1));
  private final LedgerPort ledger = mock(LedgerPort.class);
  private final DashboardAnalysisService dashboard = new DashboardAnalysisService(ledger);
  private final FinancialSummaryService summaries = new FinancialSummaryService(ledger);

  @Test
  void vehiclePaymentIsIncludedInCalendarTrendSummaryProductsAndExpenseDetail() {
    var rows = List.of(
        movement("other-charges", "-251.16", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("stellantis", "-203.95", "FINANCIACION", Movement.Kind.NORMAL, Movement.Status.BOOKED));
    setup(rows);

    assertTotals(null, "0.00", "455.11", 2);
    assertTotals("account", "0.00", "455.11", 2);
    assertThat(dashboard.calendar(period, null).getFirst().net()).isEqualByComparingTo("-455.11");
    assertThat(dashboard.calendar(period, null).get(1).expenses()).isEqualByComparingTo("0");
    assertThat(dashboard.products(period).getFirst().expenses()).isEqualByComparingTo("455.11");
    var page = dashboard.flow(period, null, FlowDirection.EXPENSE, 1, 1);
    assertThat(page.total()).isEqualByComparingTo("455.11");
    assertThat(page.movements()).extracting(DashboardAnalysisService.FlowMovement::id).containsExactly("stellantis");
    assertThat(dashboard.flow(period, null, FlowDirection.EXPENSE, "Financiación", 0, 25).total())
        .isEqualByComparingTo("203.95");
  }

  @Test
  void allViewsUseBookedComputableMovementsAndDoNotNetCreditsAgainstExpenses() {
    var rows = new ArrayList<>(List.of(
        movement("charge", "-251.16", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("vehicle", "-203.95", "FINANCIACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("saving", "-1000.10", "AHORRO_E_INVERSION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("redemption", "200.05", "AHORRO_E_INVERSION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("refund", "20.01", "ALIMENTACION", Movement.Kind.REFUND, Movement.Status.BOOKED),
        movement("salary", "3000.25", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("financing-credit", "10", "FINANCIACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("zero", "0", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("pending-debit", "-999", "FINANCIACION", Movement.Kind.NORMAL, Movement.Status.PENDING),
        movement("pending-credit", "999", "INGRESOS", Movement.Kind.REFUND, Movement.Status.PENDING),
        movement("excluded-category", "-999", "no_computable", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("excluded-refund", "999", "NO_COMPUTABLE", Movement.Kind.REFUND, Movement.Status.BOOKED)));
    for (var kind : List.of(Movement.Kind.INTERNAL_TRANSFER, Movement.Kind.CARD_SETTLEMENT,
        Movement.Kind.WALLET_SETTLEMENT, Movement.Kind.NON_COMPUTABLE, Movement.Kind.DUPLICATE)) {
      rows.add(movement(kind.name(), "-999", "ALIMENTACION", kind, Movement.Status.BOOKED));
    }
    rows.add(new Movement("other-product", "second", "other-product", period.to(), new BigDecimal("-50"),
        "EUR", "Other charge", "Other merchant", "TRANSPORTE", Movement.Kind.NORMAL, Movement.Status.BOOKED));
    setup(rows);

    assertTotals(null, "3230.31", "1505.21", 9);
    assertTotals("account", "3230.31", "1455.21", 8);
    assertTotals("second", "0", "50", 1);
    assertThat(dashboard.overview(period, null).availableAfterAllocations()).isEqualByComparingTo("1725.10");
    assertThat(summaries.summary(period, null).excluded()).isEqualTo(9);
    assertThat(summaries.summary(period, null).pending()).isEqualTo(2);
    assertThat(dashboard.products(period).stream().map(DashboardAnalysisService.ProductStat::expenses)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1505.21");
  }

  private void assertTotals(String productId, String income, String expenses, int operations) {
    var overview = dashboard.overview(period, productId);
    var summary = summaries.summary(period, productId);
    assertThat(overview.income()).isEqualByComparingTo(income);
    assertThat(overview.expenses()).isEqualByComparingTo(expenses);
    assertThat(summary.income()).isEqualByComparingTo(income);
    assertThat(summary.expenses()).isEqualByComparingTo(expenses);
    assertThat(summary.net()).isEqualByComparingTo(new BigDecimal(income).subtract(new BigDecimal(expenses)));
    assertThat(summary.availableAfterAllocations()).isEqualByComparingTo(summary.net());
    assertThat(overview.availableAfterAllocations()).isEqualByComparingTo(summary.net());
    assertThat(summary.included()).isEqualTo(operations);
    var calendar = dashboard.calendar(period, productId);
    assertThat(calendar.stream().map(DashboardAnalysisService.CalendarDay::expenses).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(expenses);
    assertThat(calendar.stream().map(DashboardAnalysisService.CalendarDay::income).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(income);
    assertThat(calendar.stream().mapToLong(DashboardAnalysisService.CalendarDay::operations).sum()).isEqualTo(operations);
    for (var grouping : List.of("DAY", "WEEK", "MONTH", "YEAR")) {
      var trend = dashboard.trend(period, productId, grouping);
      assertThat(trend.stream().map(DashboardAnalysisService.TrendPoint::expenses).reduce(BigDecimal.ZERO, BigDecimal::add))
          .isEqualByComparingTo(expenses);
      assertThat(trend.stream().map(DashboardAnalysisService.TrendPoint::income).reduce(BigDecimal.ZERO, BigDecimal::add))
          .isEqualByComparingTo(income);
    }
    assertThat(dashboard.flow(period, productId, FlowDirection.EXPENSE, 0, 1).total()).isEqualByComparingTo(expenses);
    assertThat(dashboard.flow(period, productId, FlowDirection.INCOME, 0, 1).total()).isEqualByComparingTo(income);
    assertThat(dashboard.categories(period, productId).stream().map(DashboardAnalysisService.CategoryStat::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expenses);
    assertThat(dashboard.merchants(period, productId, 100).stream().map(DashboardAnalysisService.MerchantStat::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expenses);
  }

  private void setup(List<Movement> rows) {
    when(ledger.movements(period, null)).thenReturn(rows);
    for (var id : List.of("account", "second")) {
      when(ledger.movements(period, id)).thenReturn(rows.stream().filter(m -> id.equals(m.productId())).toList());
    }
    when(ledger.products()).thenReturn(List.of(
        new Product("account", "Cuenta", Product.ProductType.ACCOUNT, "EUR", BigDecimal.ZERO, Instant.EPOCH, null, null),
        new Product("second", "Otra cuenta", Product.ProductType.ACCOUNT, "EUR", BigDecimal.ZERO, Instant.EPOCH, null, null)));
  }

  private Movement movement(String id, String amount, String category, Movement.Kind kind, Movement.Status status) {
    return new Movement(id, "account", id, day, new BigDecimal(amount), "EUR", id, id, category, kind, status);
  }
}
