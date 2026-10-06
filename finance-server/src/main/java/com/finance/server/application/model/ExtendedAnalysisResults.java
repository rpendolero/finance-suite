package com.finance.server.application.model;

import com.finance.domain.Period;
import java.math.BigDecimal;
import java.util.Map;

public final class ExtendedAnalysisResults {
  private ExtendedAnalysisResults() {}

  public record MerchantSpend(String merchant, BigDecimal expenses, int purchases) {}

  public record BudgetResult(
      String category,
      BigDecimal budget,
      BigDecimal spent,
      BigDecimal remaining,
      BigDecimal utilizationPercent) {}

  public record PairCandidate(String firstId, String secondId, String reason, String warning) {}

  public record RecurringIncrease(
      String merchant, BigDecimal previous, BigDecimal latest, BigDecimal changePercent) {}

  public record CashFlow(
      Period period, Map<String, BigDecimal> accountNetFlows, BigDecimal total, String basis) {}
}
