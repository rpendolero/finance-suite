package com.finance.server.application.service;

import com.finance.server.application.port.*;
import com.finance.server.domain.banking.BankConnection;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class BankingAuthorizationService {
  private final BankingAuthorizationPort authorizationPort;
  private final BankConnectionPort connections;
  private final Clock clock;

  public java.util.List<String> banks(String country) { return authorizationPort.banks(country); }

  public AuthorizationStart start(String bankName, String country, int consentDays) {
    String state = UUID.randomUUID().toString();
    String id = UUID.randomUUID().toString();
    Instant validUntil = clock.instant().plus(Duration.ofDays(consentDays));
    connections.save(new BankConnection(id, "ENABLE_BANKING", bankName, country, null, state, validUntil, BankConnection.Status.AUTHORIZING, null));
    var authorization = authorizationPort.start(bankName, country, state, OffsetDateTime.ofInstant(validUntil, ZoneOffset.UTC));
    log.info("Enable Banking authorization started: connectionId={}, bank={}, country={}", id, bankName, country);
    return new AuthorizationStart(id, authorization.url(), state);
  }

  public BankConnection complete(String code, String state) {
    if (state == null || state.isBlank()) throw new IllegalArgumentException("Authorization state is required");
    var connection = connections.findByState(state).orElseThrow(() -> new IllegalArgumentException("Unknown authorization state"));
    if (connection.status() != BankConnection.Status.AUTHORIZING) throw new IllegalStateException("Authorization state has already been consumed");
    var session = authorizationPort.exchangeCode(code);
    var active = new BankConnection(connection.id(), connection.provider(), connection.bankName(), connection.country(), session.id(), connection.authorizationState(), connection.validUntil(), BankConnection.Status.ACTIVE, connection.lastSyncAt());
    connections.save(active);
    log.info("Enable Banking authorization completed: connectionId={}", connection.id());
    return active;
  }

  public record AuthorizationStart(String connectionId, String authorizationUrl, String state) {}
}
