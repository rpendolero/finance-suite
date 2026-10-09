package com.finance.server.domain.banking;

public record ExternalBankAccount(
    String id,
    String connectionId,
    String externalAccountId,
    String productId,
    String name,
    String currency,
    String cashAccountType) {
  public ExternalBankAccount(String id, String connectionId, String externalAccountId,
      String productId, String name, String currency) {
    this(id, connectionId, externalAccountId, productId, name, currency, null);
  }
}
