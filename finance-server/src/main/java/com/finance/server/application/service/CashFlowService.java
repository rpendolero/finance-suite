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
public class CashFlowService {
  private final LedgerPort ledger;

  public CashFlow cashFlow(Period p) {
    var accounts =
        ledger.products().stream()
            .filter(Product::liquid)
            .map(Product::id)
            .collect(Collectors.toSet());
    var amounts = new TreeMap<String, BigDecimal>();
    ledger.movements(p, null).stream()
        .filter(m -> m.status() == Movement.Status.BOOKED && accounts.contains(m.productId()))
        .forEach(m -> amounts.merge(m.productId(), m.amount(), BigDecimal::add));
    return new CashFlow(
        p,
        amounts,
        amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
        "Flujo efectivo de cuentas: incluye pagos de tarjeta e internas. No equivale al gasto por"
            + " compras.");
  }
}
