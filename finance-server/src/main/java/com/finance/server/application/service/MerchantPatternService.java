package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Analysis.*;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MerchantPatternService {
  private final LedgerPort ledger;

  private static BigDecimal sum(List<Movement> ms) {
    return ms.stream().map(Movement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Map<String, List<Movement>> merchants(Period p) {
    return ledger.movements(p, null).stream()
        .filter(Movement::included)
        .filter(m -> m.amount().signum() < 0 && m.merchant() != null && !m.merchant().isBlank())
        .collect(Collectors.groupingBy(m -> m.merchant().strip().toUpperCase(Locale.ROOT)));
  }

  public List<Recurring> recurring(Period period) {
    var result = new ArrayList<Recurring>();
    merchants(period)
        .forEach(
            (merchant, movements) -> {
              var ordered = orderByDate(movements);
              if (hasMonthlyIntervals(ordered)) result.add(monthlyPattern(merchant, ordered));
            });
    return result.stream().sorted(Comparator.comparing(Recurring::merchant)).toList();
  }

  private List<Movement> orderByDate(List<Movement> movements) {
    return movements.stream().sorted(Comparator.comparing(Movement::date)).toList();
  }

  private boolean hasMonthlyIntervals(List<Movement> ordered) {
    if (ordered.size() < 3) return false;
    for (int i = 1; i < ordered.size(); i++) {
      long days = ChronoUnit.DAYS.between(ordered.get(i - 1).date(), ordered.get(i).date());
      if (days < 25 || days > 35) return false;
    }
    return true;
  }

  private Recurring monthlyPattern(String merchant, List<Movement> ordered) {
    var last = ordered.getLast().date();
    var average =
        sum(ordered).negate().divide(BigDecimal.valueOf(ordered.size()), 2, RoundingMode.HALF_UP);
    return new Recurring(
        merchant, ordered.size(), average, last, last.plusMonths(1), "HEURISTIC_MONTHLY");
  }

  public List<Anomaly> anomalies(Period period) {
    var result = new ArrayList<Anomaly>();
    merchants(period).forEach((merchant, movements) -> findUnexpectedIncreases(movements, result));
    return result;
  }

  private void findUnexpectedIncreases(List<Movement> movements, List<Anomaly> result) {
    var ordered =
        movements.stream()
            .sorted(Comparator.comparing(Movement::date).thenComparing(Movement::id))
            .toList();
    for (int i = 3; i < ordered.size(); i++) {
      var movement = ordered.get(i);
      var median = historicalMedian(ordered.subList(0, i));
      if (exceedsAnomalyThreshold(movement, median))
        result.add(
            new Anomaly(
                movement.id(),
                "Más del doble de la mediana anterior del comercio; revisar, no prueba de fraude",
                movement.amount()));
    }
  }

  private BigDecimal historicalMedian(List<Movement> history) {
    var amounts = history.stream().map(m -> m.amount().abs()).sorted().toList();
    return amounts.get(amounts.size() / 2);
  }

  private boolean exceedsAnomalyThreshold(Movement movement, BigDecimal median) {
    return movement.amount().abs().compareTo(median.multiply(new BigDecimal("2"))) > 0
        && movement.amount().abs().subtract(median).compareTo(new BigDecimal("25")) > 0;
  }
}
