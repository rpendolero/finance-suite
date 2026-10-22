package com.finance.server.application.service;

import com.finance.domain.Product;
import com.finance.server.domain.banking.ExternalBankAccount;

/** Validates compatibility between discovered bank accounts and local products. */
final class BankProductCompatibility {
  private BankProductCompatibility() {}

  static void validateProduct(ExternalBankAccount account, Product product) {
    if (!"XXX".equals(account.currency()) && !account.currency().equals(product.currency()))
      throw new IllegalArgumentException("La moneda de la cuenta y el producto debe coincidir");
    boolean card = product.type() == Product.ProductType.CREDIT_CARD || product.type() == Product.ProductType.DEBIT_CARD;
    if ("CARD".equals(account.cashAccountType()) && !card)
      throw new IllegalArgumentException("Vincula esta cuenta CARD a un producto de tarjeta");
    if (("CACC".equals(account.cashAccountType()) || "SVGS".equals(account.cashAccountType()))
        && product.type() != Product.ProductType.ACCOUNT)
      throw new IllegalArgumentException("Vincula esta cuenta bancaria a un producto de cuenta");
  }

}
