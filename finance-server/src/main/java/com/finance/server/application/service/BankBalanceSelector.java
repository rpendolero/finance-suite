package com.finance.server.application.service;

import com.finance.domain.Product;
import com.finance.server.application.port.BankingDataPort;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class BankBalanceSelector {
  private final BankingDataPort banking;

  java.util.Optional<BankingDataPort.Balance> selectBestBalance(String externalAccountId, Product product) {
    boolean card = product.type() == Product.ProductType.CREDIT_CARD || product.type() == Product.ProductType.DEBIT_CARD;
    List<String> balanceTypes = card ? List.of("ITBD", "CLBD") : List.of("ITAV", "CLAV", "ITBD", "CLBD");
    return banking.balanceSnapshots(externalAccountId).stream()
        .filter(b -> product.currency().equals(b.currency()) && b.type() != null && balanceTypes.contains(b.type()))
        .min(Comparator.comparingInt((BankingDataPort.Balance b) -> balanceTypes.indexOf(b.type()))
            .thenComparing(BankingDataPort.Balance::at, Comparator.nullsLast(Comparator.reverseOrder())));
  }

}
