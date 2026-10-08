package com.finance.server.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BankingDataPort {
  List<Account> accounts(String sessionId);
  List<Transaction> transactions(String accountId);

  record Account(String id, String name, String currency, String cashAccountType) {
    public Account(String id, String name, String currency) { this(id, name, currency, null); }
  }
  record Transaction(String id, LocalDate bookingDate, BigDecimal amount, String currency, String description, String merchant, boolean pending) {}
}
