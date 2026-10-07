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
public class LiquidityForecastService {
  private final LedgerPort ledger;
  private final Clock clock;

  private static BigDecimal sum(List<Movement> ms) {
    return ms.stream().map(Movement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<Product> products() {
    return ledger.products();
  }

  public Forecast forecast(Period p, int days) {
    if (days < 1 || days > 90) throw new IllegalArgumentException("Horizonte entre 1 y 90 días");

    BigDecimal balances =
        products().stream()
            .filter(Product::liquid)
            .map(Product::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var accountIds =
        products().stream().filter(Product::liquid).map(Product::id).collect(Collectors.toSet());
    var flows =
        ledger.movements(p, null).stream()
            .filter(m -> m.status() == Movement.Status.BOOKED && accountIds.contains(m.productId()))
            .toList();
    var net =
        sum(flows)
            .multiply(BigDecimal.valueOf(days))
            .divide(
                BigDecimal.valueOf(ChronoUnit.DAYS.between(p.from(), p.to()) + 1),
                2,
                RoundingMode.HALF_UP);
    return new Forecast(
        LocalDate.now(clock),
        days,
        balances,
        net,
        balances.add(net),
        "Proyección lineal de flujo de cuentas (incluye liquidaciones); no predice vencimientos. No"
            + " usar como saldo disponible; comprobar actualidad y cobertura de saldos/histórico.");
  }
}
