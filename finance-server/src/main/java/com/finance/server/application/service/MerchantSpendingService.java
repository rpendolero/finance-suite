package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Period;
import com.finance.server.application.model.ExtendedAnalysisResults.*;
import com.finance.server.application.port.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MerchantSpendingService {
  private final LedgerPort ledger;

  public List<MerchantSpend> merchants(Period p) {
    var groups =
        ledger.movements(p, null).stream()
            .filter(Movement::included)
            .filter(m -> m.amount().signum() < 0 || m.kind() == Movement.Kind.REFUND)
            .collect(
                Collectors.groupingBy(
                    m ->
                        m.merchant() == null || m.merchant().isBlank()
                            ? "UNKNOWN"
                            : m.merchant().strip().toUpperCase(Locale.ROOT)));
    return groups.entrySet().stream()
        .map(
            e ->
                new MerchantSpend(
                    e.getKey(),
                    e.getValue().stream()
                        .map(Movement::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .negate(),
                    (int) e.getValue().stream().filter(m -> m.amount().signum() < 0).count()))
        .sorted(Comparator.comparing(MerchantSpend::expenses).reversed())
        .toList();
  }
}
