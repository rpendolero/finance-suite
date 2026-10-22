package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Analysis.Forecast;
import com.finance.server.application.port.LedgerPort;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LiquidityForecastService {
  private final LedgerPort ledger;
  private final Clock clock;

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public Forecast forecast(Period period, int days) {
    if (days < 1 || days > 90) throw new IllegalArgumentException("Horizonte entre 1 y 90 días");
    LocalDate today = LocalDate.now(clock);
    LocalDate until = today.plusDays(days);
    BigDecimal balance = ledger.products().stream()
        .filter(Product::liquid).map(Product::balance).reduce(ZERO, BigDecimal::add);

    List<Movement> history = ledger.movements(period, null).stream()
        .filter(Movement::included)
        .filter(m -> !m.date().isAfter(today))
        .filter(m -> "EUR".equalsIgnoreCase(m.currency()))
        .toList();

    // Forecast recurring income and expenses explicitly; extrapolate only the residual
    // variable flow so that monthly items are never counted twice.
    Map<String, List<Movement>> groups = history.stream()
        .filter(m -> !patternKey(m).isBlank())
        .collect(Collectors.groupingBy(LiquidityForecastService::patternKey));
    Set<String> recurringIds = new HashSet<>();
    BigDecimal scheduled = ZERO;
    int recurringGroups = 0;
    for (List<Movement> group : groups.values()) {
      List<Movement> ordered = group.stream()
          .sorted(Comparator.comparing(Movement::date).thenComparing(Movement::id))
          .toList();
      if (!isMonthly(ordered)) continue;
      recurringGroups++;
      ordered.forEach(m -> recurringIds.add(m.id()));
      BigDecimal average = ordered.stream().map(Movement::amount)
          .reduce(ZERO, BigDecimal::add)
          .divide(BigDecimal.valueOf(ordered.size()), 2, RoundingMode.HALF_UP);
      LocalDate next = ordered.getLast().date().plusMonths(1);
      while (!next.isAfter(until)) {
        if (next.isAfter(today)) scheduled = scheduled.add(average);
        next = next.plusMonths(1);
      }
    }

    BigDecimal variable = history.stream().filter(m -> !recurringIds.contains(m.id()))
        .map(Movement::amount).reduce(ZERO, BigDecimal::add);
    long historicalDays = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
    BigDecimal estimatedVariable = variable.multiply(BigDecimal.valueOf(days))
        .divide(BigDecimal.valueOf(historicalDays), 2, RoundingMode.HALF_UP);
    BigDecimal net = scheduled.add(estimatedVariable);
    return new Forecast(today, days, balance, net, balance.add(net),
        "Estimación híbrida: " + recurringGroups
        + " patrones mensuales de ingresos/gastos detectados (mínimo 3 operaciones, intervalos de 25 a 35 días)"
        + " más proyección lineal de movimientos variables computables en EUR."
        + " Los patrones son heurísticos, no cobros confirmados. Excluye movimientos pendientes,"
        + " transferencias y liquidaciones. No incluye vencimientos manuales ni garantiza"
        + " liquidez; los saldos y movimientos de tarjetas pueden distorsionar la estimación.");
  }

  private static String patternKey(Movement m) {
    String label = m.normalizedMerchant();
    if (label == null || label.isBlank()) label = m.merchant();
    if (label == null || label.isBlank()) label = m.description();
    if (label == null) return "";
    // Do not mix income and expense patterns for the same counterparty.
    return (m.amount().signum() >= 0 ? "IN:" : "OUT:")
        + label.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static boolean isMonthly(List<Movement> ordered) {
    if (ordered.size() < 3) return false;
    for (int i = 1; i < ordered.size(); i++) {
      long gap = ChronoUnit.DAYS.between(ordered.get(i - 1).date(), ordered.get(i).date());
      if (gap < 25 || gap > 35) return false;
    }
    return true;
  }
}
