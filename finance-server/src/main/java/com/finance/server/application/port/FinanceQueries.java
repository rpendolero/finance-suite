package com.finance.server.application.port;

import com.finance.domain.*;
import com.finance.domain.Analysis.*;
import java.util.List;

public interface FinanceQueries {
  List<Product> products();

  List<Movement> movements(Period period, String productId, int offset, int limit);

  Summary summary(Period period, String productId);

  Summary providerSummary(Period period, Product.Provider provider);

  Comparison compare(Period current, Period previous, String productId);

  List<Recurring> recurring(Period period);

  List<Anomaly> anomalies(Period period);

  List<CardExposure> cards();

  Forecast forecast(Period history, int days);

  Quality quality(Period period);
}
