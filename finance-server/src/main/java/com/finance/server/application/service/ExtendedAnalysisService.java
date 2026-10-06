package com.finance.server.application.service;

import com.finance.domain.Analysis;
import com.finance.domain.Period;
import com.finance.server.application.model.ExtendedAnalysisResults.*;
import com.finance.server.application.port.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Coordinates extended queries without owning their calculation algorithms. */
@RequiredArgsConstructor
@Slf4j
public final class ExtendedAnalysisService {
  private final MonthlyTrendService trends;
  private final MerchantSpendingService merchants;
  private final BudgetAnalysisService budgets;
  private final ReconciliationService reconciliation;
  private final RecurringIncreaseService increases;
  private final CashFlowService cashFlow;

  public ExtendedAnalysisService(FinanceQueries queries, LedgerPort ledger, SettingsPort settings) {
    this(
        new MonthlyTrendService(queries),
        new MerchantSpendingService(ledger),
        new BudgetAnalysisService(queries, settings),
        new ReconciliationService(ledger),
        new RecurringIncreaseService(queries, ledger),
        new CashFlowService(ledger));
  }

  public List<Analysis.Summary> trend(String from, String to) {
    log.debug("Executing trend query");
    return trends.trend(from, to);
  }

  public List<MerchantSpend> merchants(Period period) {
    log.debug("Executing merchants query");
    return merchants.merchants(period);
  }

  public List<BudgetResult> budgets(String month) {
    log.debug("Executing budgets query");
    return budgets.budgets(month);
  }

  public List<PairCandidate> reconciliation(Period period) {
    log.debug("Executing reconciliation query");
    return reconciliation.reconciliation(period);
  }

  public List<RecurringIncrease> increases(Period period) {
    log.debug("Executing increases query");
    return increases.increases(period);
  }

  public CashFlow cashFlow(Period period) {
    log.debug("Executing cashFlow query");
    return cashFlow.cashFlow(period);
  }
}
