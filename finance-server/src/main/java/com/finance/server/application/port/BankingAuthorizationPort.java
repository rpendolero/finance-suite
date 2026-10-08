package com.finance.server.application.port;

import java.time.OffsetDateTime;

public interface BankingAuthorizationPort {
  Authorization start(String bankName, String country, String state, OffsetDateTime validUntil);
  java.util.List<String> banks(String country);
  Session exchangeCode(String code);

  record Authorization(String url) {}
  record Session(String id) {}
}
