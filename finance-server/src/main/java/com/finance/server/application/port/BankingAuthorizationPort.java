package com.finance.server.application.port;

import java.time.OffsetDateTime;

public interface BankingAuthorizationPort {
  Authorization start(String bankName, String country, String state, OffsetDateTime validUntil);
  Session exchangeCode(String code);

  record Authorization(String url) {}
  record Session(String id) {}
}
