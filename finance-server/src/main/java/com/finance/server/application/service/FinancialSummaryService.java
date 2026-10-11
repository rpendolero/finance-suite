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
public class FinancialSummaryService {
  private final LedgerPort ledger;

  private static BigDecimal sum(List<Movement> ms) {
    return ms.stream().map(Movement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public Summary summary(Period p, String id) {
    var all = ledger.movements(p, id);
    return summarize(p, all);
  }

  public Summary providerSummary(Period p, Product.Provider provider) {
    var ids =
        ledger.products().stream()
            .filter(x -> x.provider() == provider)
            .map(Product::id)
            .collect(Collectors.toSet());
    return summarize(
        p, ledger.movements(p, null).stream().filter(x -> ids.contains(x.productId())).toList());
  }

  private Summary summarize(Period p, List<Movement> all) {
    var included = all.stream().filter(Movement::included).toList();
    BigDecimal income = FinancialMovementAllocation.total(included,
        FinancialMovementAllocation::income, false);
    BigDecimal expenses = FinancialMovementAllocation.total(included,
        FinancialMovementAllocation::consumption, true);
    BigDecimal investment = FinancialMovementAllocation.total(included,
        FinancialMovementAllocation::saving, true);
    BigDecimal financing = FinancialMovementAllocation.total(included,
        FinancialMovementAllocation::financing, true);

    var categories = new TreeMap<String, BigDecimal>();
    included.stream().filter(FinancialMovementAllocation::consumption)
        .forEach(m -> categories.merge(expenseCategory(m),
            FinancialMovementAllocation.paid(m), BigDecimal::add));

    BigDecimal operatingNet = income.subtract(expenses);
    BigDecimal savingsRate = income.signum() > 0
        ? investment.multiply(new BigDecimal("100"))
            .divide(income, 2, RoundingMode.HALF_UP)
        : null;
    BigDecimal operatingMarginRate = income.signum() > 0
        ? operatingNet.multiply(new BigDecimal("100"))
            .divide(income, 2, RoundingMode.HALF_UP)
        : null;
    return new Summary(p, income, expenses, operatingNet, savingsRate, categories,
        included.size(), all.size() - included.size(),
        (int) all.stream().filter(m -> m.status() == Movement.Status.PENDING).count(),
        "Consumo sin inversiones ni financiación; cuotas de deuda sin desglose no "
            + "se imputan a gastos. Disponible calculado antes de otros movimientos excluidos.",
        investment, financing, operatingNet.subtract(investment).subtract(financing),
        operatingMarginRate);
  }

  private String expenseCategory(Movement movement) {
    return "INGRESOS".equalsIgnoreCase(movement.category())
            && "DEVOLUCIONES".equalsIgnoreCase(movement.subcategory())
        ? "DEVOLUCIONES_SIN_ASOCIAR" : movement.category();
  }

  public Comparison compare(Period a, Period b, String id) {
    var x = summary(a, id);
    var y = summary(b, id);
    return new Comparison(
        x,
        y,
        x.income().subtract(y.income()),
        x.expenses().subtract(y.expenses()),
        x.net().subtract(y.net()));
  }
}
