package com.finance.server.domain.banking;

public record ExternalBankAccount(
    String id,
    String connectionId,
    String externalAccountId,
    String productId,
    String name,
    String currency,
    String cashAccountType,
    String iban) {
  public ExternalBankAccount(String id, String connectionId, String externalAccountId,
      String productId, String name, String currency, String cashAccountType) {
    this(id, connectionId, externalAccountId, productId, name, currency, cashAccountType, null);
  }
  public ExternalBankAccount(String id, String connectionId, String externalAccountId,
      String productId, String name, String currency) {
    this(id, connectionId, externalAccountId, productId, name, currency, null);
  }
}
