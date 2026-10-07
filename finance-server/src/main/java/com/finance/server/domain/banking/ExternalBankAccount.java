package com.finance.server.domain.banking;

public record ExternalBankAccount(
    String id,
    String connectionId,
    String externalAccountId,
    String productId,
    String name,
    String currency) {}
