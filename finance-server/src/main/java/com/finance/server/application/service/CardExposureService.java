package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.domain.Analysis.*;
import com.finance.server.application.port.LedgerPort;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CardExposureService {
  private final LedgerPort ledger;

  private List<Product> products() {
    return ledger.products();
  }

  public List<CardExposure> cards() {
    return products().stream()
        .filter(p -> p.type() == Product.ProductType.CREDIT_CARD)
        .map(
            p -> {
              var debt = p.balance().signum() < 0 ? p.balance().negate() : BigDecimal.ZERO;
              var limit = p.creditLimit();
              return new CardExposure(
                  p.id(),
                  p.balance(),
                  limit,
                  limit == null ? null : limit.subtract(debt),
                  limit != null && limit.signum() > 0
                      ? debt.multiply(new BigDecimal("100")).divide(limit, 2, RoundingMode.HALF_UP)
                      : null,
                  "Saldo negativo = deuda. Saldo y límite declarados/importados; sin cálculo de"
                      + " intereses ni fecha de vencimiento.");
            })
        .toList();
  }
}
