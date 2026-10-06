package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Analysis.*;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DataQualityService {
  private final LedgerPort ledger;
  private final Clock clock;

  private List<Product> products() {
    return ledger.products();
  }

  public Quality quality(Period p) {
    var ms = ledger.movements(p, null);
    var warnings = new ArrayList<String>();
    if (ms.isEmpty()) warnings.add("Sin movimientos en el periodo");
    if (ms.stream().anyMatch(m -> !m.included()))
      warnings.add("Hay movimientos excluidos: validar cobertura de compras y liquidaciones");
    if (products().stream()
        .anyMatch(x -> x.balanceAt().isBefore(clock.instant().minus(Duration.ofDays(2)))))
      warnings.add("Saldos con más de 48 horas de antigüedad");
    warnings.add("Cobertura histórica no certificada; no hay conciliación automática con el banco");
    var groups =
        ms.stream()
            .collect(
                Collectors.groupingBy(
                    m ->
                        m.productId() + "|" + m.date() + "|" + m.amount() + "|" + m.description()));
    int duplicates = groups.values().stream().mapToInt(g -> Math.max(0, g.size() - 1)).sum();
    return new Quality(
        products().size(),
        ms.size(),
        (int) ms.stream().filter(m -> m.status() == Movement.Status.PENDING).count(),
        (int) ms.stream().filter(m -> m.category().equals("UNCLASSIFIED")).count(),
        (int) ms.stream().filter(m -> !m.included()).count(),
        duplicates,
        ms.stream().map(Movement::date).min(LocalDate::compareTo).orElse(null),
        ms.stream().map(Movement::date).max(LocalDate::compareTo).orElse(null),
        warnings);
  }
}
