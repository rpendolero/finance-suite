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
public class BudgetAnalysisService {
  private final FinanceQueries queries;
  private final SettingsPort settings;

  public List<BudgetResult> budgets(String month) {
    var m = YearMonth.parse(month);
    var spent =
        queries.summary(new Period(m.atDay(1), m.atEndOfMonth()), null).expensesByCategory();
    return settings.budgets(month).stream()
        .map(
            b -> {
              var actual = spent.getOrDefault(b.category(), BigDecimal.ZERO);
              return new BudgetResult(
                  b.category(),
                  b.amount(),
                  actual,
                  b.amount().subtract(actual),
                  b.amount().signum() > 0
                      ? actual
                          .multiply(new BigDecimal("100"))
                          .divide(b.amount(), 2, RoundingMode.HALF_UP)
                      : null);
            })
        .toList();
  }
}
