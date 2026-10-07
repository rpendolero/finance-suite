package com.finance.server.domain.banking;

import java.time.Instant;

public record BankConnection(
    String id,
    String provider,
    String bankName,
    String country,
    String externalSessionId,
    String authorizationState,
    Instant validUntil,
    Status status,
    Instant lastSyncAt) {
  public enum Status { AUTHORIZING, ACTIVE, REAUTH_REQUIRED, ERROR, DISCONNECTED }
}
