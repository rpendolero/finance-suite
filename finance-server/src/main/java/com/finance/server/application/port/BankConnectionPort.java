package com.finance.server.application.port;

import com.finance.server.domain.banking.*;
import java.util.*;

public interface BankConnectionPort {
  BankConnection save(BankConnection connection);
  Optional<BankConnection> find(String id);
  Optional<BankConnection> findByState(String state);
  List<BankConnection> findAll();
  ExternalBankAccount saveAccount(ExternalBankAccount account);
  List<ExternalBankAccount> accounts(String connectionId);
}
