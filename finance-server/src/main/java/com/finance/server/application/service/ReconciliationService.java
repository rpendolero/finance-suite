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
public class ReconciliationService {
  private final LedgerPort ledger;

  private static final int MAX_CANDIDATES = 200;

  public List<PairCandidate> reconciliation(Period period) {
    var movements = bookedNormalMovements(period);
    var candidates = new ArrayList<PairCandidate>();
    findOppositeTransfers(movements, candidates);
    if (candidates.size() < MAX_CANDIDATES) findLinkedPaymentDuplicates(movements, candidates);
    return candidates;
  }

  private List<Movement> bookedNormalMovements(Period period) {
    return ledger.movements(period, null).stream()
        .filter(m -> m.status() == Movement.Status.BOOKED && m.kind() == Movement.Kind.NORMAL)
        .toList();
  }

  private void findOppositeTransfers(List<Movement> movements, List<PairCandidate> candidates) {
    var positive =
        movements.stream()
            .filter(m -> m.amount().signum() > 0)
            .collect(Collectors.groupingBy(m -> m.amount().stripTrailingZeros()));
    for (var debit : movements) {
      if (debit.amount().signum() >= 0) continue;
      for (var credit :
          positive.getOrDefault(debit.amount().negate().stripTrailingZeros(), List.of())) {
        if (differentProductsWithinDays(debit, credit, 3)) {
          candidates.add(
              new PairCandidate(
                  debit.id(),
                  credit.id(),
                  "Importes opuestos en productos distintos con separación <=3 días",
                  "Candidato: confirma si es transferencia propia, pago de tarjeta u otra"
                      + " operación. No se excluye automáticamente."));
          if (candidates.size() >= MAX_CANDIDATES) return;
        }
      }
    }
  }

  private void findLinkedPaymentDuplicates(
      List<Movement> movements, List<PairCandidate> candidates) {
    var products = ledger.products().stream().collect(Collectors.toMap(Product::id, p -> p));
    var negative =
        movements.stream()
            .filter(m -> m.amount().signum() < 0)
            .collect(Collectors.groupingBy(m -> m.amount().stripTrailingZeros()));
    for (var group : negative.values()) {
      for (var purchase : group) {
        var product = products.get(purchase.productId());
        if (!canDuplicateLinkedAccount(product)) continue;
        for (var charge : group) {
          if (product.linkedAccountId().equals(charge.productId())
              && withinDays(purchase, charge, 2)) {
            candidates.add(
                new PairCandidate(
                    purchase.id(),
                    charge.id(),
                    "Posible compra repetida en cuenta y tarjeta/PayPal",
                    "Confirmar manualmente y marcar una copia DUPLICATE; dos compras legítimas"
                        + " pueden coincidir."));
            if (candidates.size() >= MAX_CANDIDATES) return;
          }
        }
      }
    }
  }

  private boolean canDuplicateLinkedAccount(Product product) {
    return product != null
        && product.linkedAccountId() != null
        && (product.type() == Product.ProductType.DEBIT_CARD
            || product.provider() == Product.Provider.PAYPAL);
  }

  private boolean differentProductsWithinDays(Movement first, Movement second, int days) {
    return !first.productId().equals(second.productId()) && withinDays(first, second, days);
  }

  private boolean withinDays(Movement first, Movement second, int days) {
    return Math.abs(java.time.temporal.ChronoUnit.DAYS.between(first.date(), second.date()))
        <= days;
  }
}
