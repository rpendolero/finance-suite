package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Analysis.*;
import com.finance.domain.Period;
import com.finance.server.application.port.*;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Query facade: delegates each financial responsibility to its dedicated service. */
@RequiredArgsConstructor
@Slf4j
public class FinanceService implements FinanceQueries {
  private final LedgerPort ledger;
  private final FinancialSummaryService summaries;
  private final MerchantPatternService patterns;
  private final CardExposureService exposure;
  private final LiquidityForecastService forecasts;
  private final DataQualityService quality;

  public FinanceService(LedgerPort ledger, Clock clock) {
    this(
        ledger,
        new FinancialSummaryService(ledger),
        new MerchantPatternService(ledger),
        new CardExposureService(ledger),
        new LiquidityForecastService(ledger, clock),
        new DataQualityService(ledger, clock));
  }

  public List<Product> products() {
    log.debug("Executing products query");
    return ledger.products();
  }

  public List<Movement> movements(Period period, String productId, int offset, int limit) {
    log.debug("Executing movements query");
    validatePagination(offset, limit);
    return ledger.movements(period, productId).stream().skip(offset).limit(limit).toList();
  }

  private void validatePagination(int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 500)
      throw new IllegalArgumentException("offset>=0; limit entre 1 y 500");
  }

  public Summary summary(Period period, String productId) {
    log.debug("Executing summary query");
    return summaries.summary(period, productId);
  }

  public Summary providerSummary(Period period, Product.Provider provider) {
    log.debug("Executing providerSummary query");
    return summaries.providerSummary(period, provider);
  }

  public Comparison compare(Period first, Period second, String productId) {
    log.debug("Executing compare query");
    return summaries.compare(first, second, productId);
  }

  public List<Recurring> recurring(Period period) {
    log.debug("Executing recurring query");
    return patterns.recurring(period);
  }

  public List<Anomaly> anomalies(Period period) {
    log.debug("Executing anomalies query");
    return patterns.anomalies(period);
  }

  public List<CardExposure> cards() {
    log.debug("Executing cards query");
    return exposure.cards();
  }

  public Forecast forecast(Period period, int days) {
    log.debug("Executing forecast query");
    return forecasts.forecast(period, days);
  }

  public Quality quality(Period period) {
    log.debug("Executing quality query");
    return quality.quality(period);
  }
}
