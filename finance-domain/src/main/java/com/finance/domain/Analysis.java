package com.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Analysis {
  private Analysis() {}

  public record Summary(
      Period period,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal net,
      BigDecimal savingsRatePercent,
      Map<String, BigDecimal> expensesByCategory,
      int included,
      int excluded,
      int pending,
      String basis) {}

  public record Comparison(
      Summary current,
      Summary previous,
      BigDecimal incomeDelta,
      BigDecimal expenseDelta,
      BigDecimal netDelta) {}

  public record Recurring(
      String merchant,
      int occurrences,
      BigDecimal average,
      LocalDate lastDate,
      LocalDate estimatedNextDate,
      String confidence) {}

  public record Anomaly(String movementId, String reason, BigDecimal amount) {}

  public record CardExposure(
      String productId,
      BigDecimal balance,
      BigDecimal creditLimit,
      BigDecimal availableCredit,
      BigDecimal utilizationPercent,
      String note) {}

  public record Forecast(
      LocalDate asOf,
      int days,
      BigDecimal accountBalances,
      BigDecimal estimatedNetFlow,
      BigDecimal estimatedAccountBalance,
      String limitations) {}

  public record Quality(
      int products,
      int movements,
      int pending,
      int unclassified,
      int excluded,
      int potentialDuplicates,
      LocalDate firstDate,
      LocalDate lastDate,
      List<String> warnings) {}
}
