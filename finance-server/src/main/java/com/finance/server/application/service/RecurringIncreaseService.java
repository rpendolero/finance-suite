package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Period;
import com.finance.server.application.model.ExtendedAnalysisResults.*;
import com.finance.server.application.port.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RecurringIncreaseService {
  private final FinanceQueries queries;
  private final LedgerPort ledger;

  public List<RecurringIncrease> increases(Period p) {
    var recurring = queries.recurring(p);
    var all = ledger.movements(p, null);
    var out = new ArrayList<RecurringIncrease>();
    for (var r : recurring) {
      var ms =
          all.stream()
              .filter(Movement::included)
              .filter(
                  m ->
                      m.amount().signum() < 0
                          && m.merchant() != null
                          && m.merchant().strip().equalsIgnoreCase(r.merchant()))
              .sorted(Comparator.comparing(Movement::date))
              .toList();
      var latest = ms.get(ms.size() - 1).amount().abs();
      var prev = ms.get(ms.size() - 2).amount().abs();
      if (prev.signum() > 0 && latest.compareTo(prev) > 0)
        out.add(
            new RecurringIncrease(
                r.merchant(),
                prev,
                latest,
                latest
                    .subtract(prev)
                    .multiply(new BigDecimal("100"))
                    .divide(prev, 2, RoundingMode.HALF_UP)));
    }
    return out;
  }
}
