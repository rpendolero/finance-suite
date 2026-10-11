package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finance.domain.Movement;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.DashboardAnalysisService;
import com.finance.server.application.service.FinancialSummaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialAllocationTest {

  @Test
  void keepsInvestmentsAndDebtPaymentsOutsideConsumptionAndIncome() {
    Period period = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    LedgerPort ledger = mock(LedgerPort.class);
    List<Movement> rows = List.of(
        movement("salary", "3000", "INGRESOS", "NOMINA"),
        movement("food", "-500", "ALIMENTACION", "SUPERMERCADO"),
        movement("reversal", "100", "INGRESOS", "DEVOLUCIONES"),
        movement("pension", "-600", "AHORRO_E_INVERSION", "PLAN_DE_PENSIONES"),
        movement("redemption", "100", "AHORRO_E_INVERSION", "FONDOS_INVERSION"),
        movement("loan", "-400", "FINANCIACION", "PRESTAMO_PERSONAL"),
        movement("principal", "-50", "FINANCIACION", "AMORTIZACION_CAPITAL"),
        movement("interest", "-10", "FINANCIACION", "INTERESES"),
        movement("excluded", "-900", "NO_COMPUTABLE", "LIQUIDACION_TARJETA",
            Movement.Kind.CARD_SETTLEMENT)
    );
    when(ledger.movements(period, null)).thenReturn(rows);
    when(ledger.products()).thenReturn(List.of());

    var dashboard = new DashboardAnalysisService(ledger);
    var overview = dashboard.overview(period, null);
    assertThat(overview.income()).isEqualByComparingTo("3000");
    assertThat(overview.expenses()).isEqualByComparingTo("410");
    assertThat(overview.savings()).isEqualByComparingTo("500");
    assertThat(overview.financingPayments()).isEqualByComparingTo("460");
    assertThat(overview.financingCapital()).isEqualByComparingTo("50");
    assertThat(overview.financingInterest()).isEqualByComparingTo("10");
    assertThat(overview.financingUnallocated()).isEqualByComparingTo("400");
    assertThat(overview.availableAfterAllocations()).isEqualByComparingTo("1640");
    assertThat(overview.savingsRate()).isEqualByComparingTo("16.67");

    var investments = dashboard.flow(period, null,
        DashboardAnalysisService.FlowDirection.SAVINGS, 0, 25);
    assertThat(investments.total()).isEqualByComparingTo("500");
    assertThat(investments.operations()).isEqualTo(2);
    var financing = dashboard.flow(period, null,
        DashboardAnalysisService.FlowDirection.FINANCING, 0, 25);
    assertThat(financing.total()).isEqualByComparingTo("460");
    assertThat(financing.operations()).isEqualTo(3);
    assertThat(dashboard.flow(period, null,
        DashboardAnalysisService.FlowDirection.INCOME, 0, 25).operations()).isEqualTo(1);
    assertThat(dashboard.flow(period, null,
        DashboardAnalysisService.FlowDirection.EXPENSE, 0, 25).total()).isEqualByComparingTo("410");

    var summary = new FinancialSummaryService(ledger).summary(period, null);
    assertThat(summary.income()).isEqualByComparingTo("3000");
    assertThat(summary.expenses()).isEqualByComparingTo("410");
    assertThat(summary.investmentContributions()).isEqualByComparingTo("500");
    assertThat(summary.financingPayments()).isEqualByComparingTo("460");
    assertThat(summary.availableAfterAllocations()).isEqualByComparingTo("1640");
    assertThat(summary.expensesByCategory()).containsEntry("DEVOLUCIONES_SIN_ASOCIAR",
        new BigDecimal("-100"));
  }

  private Movement movement(String id, String amount, String category, String subcategory) {
    return movement(id, amount, category, subcategory, Movement.Kind.NORMAL);
  }

  private Movement movement(String id, String amount, String category, String subcategory,
      Movement.Kind kind) {
    return new Movement(id, "account", id, LocalDate.of(2026, 10, 5),
        new BigDecimal(amount), "EUR", id, id, category, kind, Movement.Status.BOOKED,
        null, subcategory, Movement.ClassificationSource.MANUAL, BigDecimal.ONE);
  }
}
