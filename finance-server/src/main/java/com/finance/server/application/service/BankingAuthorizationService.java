package com.finance.server.application.service;

import com.finance.server.application.port.BankingAuthorizationPort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class BankingAuthorizationService {
  private final BankingAuthorizationPort authorizationPort;
  private final Clock clock;

  public AuthorizationStart start(String bankName, String country, int consentDays) {
    String state = UUID.randomUUID().toString();
    OffsetDateTime validUntil = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(consentDays);
    var authorization = authorizationPort.start(bankName, country, state, validUntil);
    log.info("Enable Banking authorization started: bank={}, country={}", bankName, country);
    return new AuthorizationStart(authorization.url(), state);
  }

  public BankingAuthorizationPort.Session complete(String code) {
    var session = authorizationPort.exchangeCode(code);
    log.info("Enable Banking authorization completed");
    return session;
  }

  public record AuthorizationStart(String authorizationUrl, String state) {}
}
