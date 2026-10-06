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
    var ms = all.stream().filter(Movement::included).toList();
    BigDecimal
        income =
            sum(
                ms.stream()
                    .filter(m -> m.amount().signum() > 0 && m.kind() != Movement.Kind.REFUND)
                    .toList()),
        expenses =
            sum(ms.stream()
                    .filter(m -> m.amount().signum() < 0 || m.kind() == Movement.Kind.REFUND)
                    .toList())
                .negate();
    var categories = new TreeMap<String, BigDecimal>();
    ms.stream()
        .filter(m -> m.amount().signum() < 0 || m.kind() == Movement.Kind.REFUND)
        .forEach(m -> categories.merge(m.category(), m.amount().negate(), BigDecimal::add));
    BigDecimal net = income.subtract(expenses),
        rate =
            income.signum() > 0
                ? net.multiply(new BigDecimal("100")).divide(income, 2, RoundingMode.HALF_UP)
                : null;
    return new Summary(
        p,
        income,
        expenses,
        net,
        rate,
        categories,
        ms.size(),
        all.size() - ms.size(),
        (int) all.stream().filter(m -> m.status() == Movement.Status.PENDING).count(),
        "Compras contabilizadas por fecha; liquidaciones y transferencias internas excluidas."
            + " Devoluciones marcadas REFUND reducen el gasto de su categoría.");
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
