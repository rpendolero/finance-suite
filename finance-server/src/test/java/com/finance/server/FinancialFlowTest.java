package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.domain.Movement;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.DashboardAnalysisService;
import com.finance.server.application.service.DashboardAnalysisService.FlowDirection;
import com.finance.server.infrastructure.adapter.in.rest.ApiErrors;
import com.finance.server.infrastructure.adapter.in.rest.DashboardController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FinancialFlowTest {
  private final LedgerPort ledger = mock(LedgerPort.class);
  private final DashboardAnalysisService service = new DashboardAnalysisService(ledger);
  private final Period period = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

  @Test
  void incomeUsesSignRegardlessOfCategoryAndExcludesRefundsPendingAndTransfers() {
    when(ledger.movements(period, null)).thenReturn(List.of(
        movement("salary", "2000", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("credit", "10", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("debit", "-100", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("refund", "20", "ALIMENTACION", Movement.Kind.REFUND, Movement.Status.BOOKED),
        movement("transfer", "1000", "NO_COMPUTABLE", Movement.Kind.INTERNAL_TRANSFER, Movement.Status.BOOKED),
        movement("pending", "3000", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.PENDING),
        movement("zero", "0", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED)));

    var result = service.flow(period, null, FlowDirection.INCOME, 0, 25);
    assertThat(result.total()).isEqualByComparingTo("2010");
    assertThat(result.operations()).isEqualTo(2);
    assertThat(result.movements()).extracting(DashboardAnalysisService.FlowMovement::id)
        .containsExactlyInAnyOrder("salary", "credit");
    assertThat(result.categories()).extracting(DashboardAnalysisService.CategoryStat::category)
        .containsExactly("INGRESOS", "ALIMENTACION");
    assertThat(result.categories().getFirst().amount()).isEqualByComparingTo("2000");
    assertThat(result.counterparties().getFirst().merchant()).isEqualTo("salary");
    assertThat(result.counterparties().getFirst().amount()).isEqualByComparingTo("2000");
  }

  @Test
  void refundsReduceExpenseTotalsAndRowsKeepOriginalSignedAmounts() {
    when(ledger.movements(period, null)).thenReturn(List.of(
        movement("purchase", "-100", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("refund", "20", "ALIMENTACION", Movement.Kind.REFUND, Movement.Status.BOOKED),
        movement("salary", "2000", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("excluded", "-500", "NO_COMPUTABLE", Movement.Kind.NON_COMPUTABLE, Movement.Status.BOOKED),
        movement("pending", "-200", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.PENDING)));

    var result = service.flow(period, null, FlowDirection.EXPENSE, 0, 25);
    assertThat(result.total()).isEqualByComparingTo("80");
    assertThat(result.operations()).isEqualTo(2);
    assertThat(result.categories()).hasSize(1);
    assertThat(result.categories().getFirst().amount()).isEqualByComparingTo("80");
    assertThat(result.categories().getFirst().share()).isEqualByComparingTo("100");
    assertThat(result.movements()).extracting(DashboardAnalysisService.FlowMovement::amount)
        .containsExactlyInAnyOrder(new BigDecimal("-100"), new BigDecimal("20"));
  }

  @Test
  void paginationKeepsFullPeriodTotalsAndUsesStableOrder() {
    when(ledger.movements(period, "account")).thenReturn(List.of(
        movement("b", "20", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("a", "10", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED)));
    var result = service.flow(period, "account", FlowDirection.INCOME, 1, 1);
    assertThat(result.total()).isEqualByComparingTo("30");
    assertThat(result.operations()).isEqualTo(2);
    assertThat(result.offset()).isEqualTo(1);
    assertThat(result.movements()).extracting(DashboardAnalysisService.FlowMovement::id).containsExactly("b");
  }

  @Test
  void categoryFilterAppliesBeforePaginationAndTotalsExcludeOtherCategories() {
    when(ledger.movements(period, null)).thenReturn(List.of(
        movement("a", "-100", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("b", "20", "ALIMENTACION", Movement.Kind.REFUND, Movement.Status.BOOKED),
        movement("c", "-500", "TRANSPORTE", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("d", "50", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("e", "-10", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.PENDING)));

    var expense = service.flow(period, null, FlowDirection.EXPENSE, "ALIMENTACION", 1, 1);
    assertThat(expense.total()).isEqualByComparingTo("80");
    assertThat(expense.operations()).isEqualTo(2);
    assertThat(expense.movements()).extracting(DashboardAnalysisService.FlowMovement::id).containsExactly("b");
    assertThat(expense.categories()).extracting(DashboardAnalysisService.CategoryStat::category).containsExactly("ALIMENTACION");
    var income = service.flow(period, null, FlowDirection.INCOME, "ALIMENTACION", 0, 25);
    assertThat(income.total()).isEqualByComparingTo("50");
    assertThat(income.movements()).extracting(DashboardAnalysisService.FlowMovement::id).containsExactly("d");
    assertThat(service.flow(period, null, FlowDirection.EXPENSE, "UNKNOWN", 0, 25).movements()).isEmpty();
  }

  @Test
  void restCategoryFilterReturnsOnlySelectedCategory() throws Exception {
    when(ledger.movements(period, null)).thenReturn(List.of(
        movement("a", "-10", "Regalos y ocio", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("b", "-20", "Regalos y ocio", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("c", "-500", "TRANSPORTE", Movement.Kind.NORMAL, Movement.Status.BOOKED)));
    var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
    mvc.perform(get("/api/dashboard/flows").param("from", "2026-10-01").param("to", "2026-10-31")
            .param("category", "Regalos y ocio").param("offset", "1").param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(30))
        .andExpect(jsonPath("$.operations").value(2))
        .andExpect(jsonPath("$.movements.length()").value(1))
        .andExpect(jsonPath("$.movements[0].id").value("b"))
        .andExpect(jsonPath("$.movements[0].category").value("Regalos y ocio"));
  }

  @Test
  void emptyPeriodHasZeroTotalAndInvalidPaginationIsRejected() {
    when(ledger.movements(period, null)).thenReturn(List.of());
    var result = service.flow(period, null, FlowDirection.INCOME, 0, 25);
    assertThat(result.total()).isEqualByComparingTo("0");
    assertThat(result.movements()).isEmpty();
    assertThat(result.categories()).isEmpty();
    assertThat(result.counterparties()).isEmpty();
    assertThatThrownBy(() -> service.flow(period, null, FlowDirection.INCOME, -1, 25))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.flow(period, null, FlowDirection.INCOME, 0, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restResponseSeparatesDirectionsAndSerializesSignedRows() throws Exception {
    when(ledger.movements(period, null)).thenReturn(List.of(
        movement("salary", "2000", "INGRESOS", Movement.Kind.NORMAL, Movement.Status.BOOKED),
        movement("purchase", "-100", "ALIMENTACION", Movement.Kind.NORMAL, Movement.Status.BOOKED)));
    var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service))
        .setControllerAdvice(new ApiErrors()).build();
    mvc.perform(get("/api/dashboard/flows").param("from", "2026-10-01").param("to", "2026-10-31")
            .param("direction", "INCOME"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2000))
        .andExpect(jsonPath("$.direction").value("INCOME"))
        .andExpect(jsonPath("$.movements[0].amount").value(2000));
    mvc.perform(get("/api/dashboard/flows").param("from", "2026-10-01").param("to", "2026-10-31"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(100))
        .andExpect(jsonPath("$.direction").value("EXPENSE"))
        .andExpect(jsonPath("$.movements[0].amount").value(-100));
    mvc.perform(get("/api/dashboard/flows").param("from", "2026-10-01").param("to", "2026-10-31")
            .param("limit", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/dashboard/flows").param("from", "2026-10-01").param("to", "2026-10-31")
            .param("direction", "INVALID"))
        .andExpect(status().isBadRequest());
  }

  private Movement movement(String id, String amount, String category, Movement.Kind kind, Movement.Status status) {
    return new Movement(id, "account", id, period.from(), new BigDecimal(amount), "EUR",
        id, id, category, kind, status);
  }
}
