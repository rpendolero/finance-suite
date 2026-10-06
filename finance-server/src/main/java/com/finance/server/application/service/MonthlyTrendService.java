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
public class MonthlyTrendService {
  private final FinanceQueries queries;

  public List<Analysis.Summary> trend(String fromMonth, String toMonth) {
    var start = YearMonth.parse(fromMonth);
    var end = YearMonth.parse(toMonth);
    if (end.isBefore(start) || java.time.temporal.ChronoUnit.MONTHS.between(start, end) > 35)
      throw new IllegalArgumentException("Máximo 36 meses");
    var out = new ArrayList<Analysis.Summary>();
    for (var m = start; !m.isAfter(end); m = m.plusMonths(1))
      out.add(queries.summary(new Period(m.atDay(1), m.atEndOfMonth()), null));
    return out;
  }
}
