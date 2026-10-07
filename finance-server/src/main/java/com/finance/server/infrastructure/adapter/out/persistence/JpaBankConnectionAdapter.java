package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.server.application.port.BankConnectionPort;
import com.finance.server.domain.banking.*;
import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import com.finance.server.infrastructure.adapter.out.persistence.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JpaBankConnectionAdapter implements BankConnectionPort {
  private final BankConnectionRepository connections;
  private final ExternalBankAccountRepository accounts;

  @Override @Transactional
  public BankConnection save(BankConnection c) {
    var e = new BankConnectionEntity();
    e.setId(c.id()); e.setProvider(c.provider()); e.setBankName(c.bankName()); e.setCountry(c.country());
    e.setExternalSessionId(c.externalSessionId()); e.setAuthorizationState(c.authorizationState());
    e.setValidUntil(c.validUntil()); e.setStatus(c.status()); e.setLastSyncAt(c.lastSyncAt());
    connections.saveAndFlush(e);
    log.debug("Bank connection persisted: id={}, provider={}, status={}", c.id(), c.provider(), c.status());
    return c;
  }
  @Override public Optional<BankConnection> find(String id) { return connections.findById(id).map(this::domain); }
  @Override public Optional<BankConnection> findByState(String state) { return connections.findByAuthorizationState(state).map(this::domain); }
  @Override public List<BankConnection> findAll() { return connections.findAll().stream().map(this::domain).toList(); }

  @Override @Transactional
  public ExternalBankAccount saveAccount(ExternalBankAccount a) {
    var e = new ExternalBankAccountEntity();
    e.setId(a.id()); e.setConnectionId(a.connectionId()); e.setExternalAccountId(a.externalAccountId());
    e.setProductId(a.productId()); e.setName(a.name()); e.setCurrency(a.currency());
    accounts.saveAndFlush(e); return a;
  }
  @Override public List<ExternalBankAccount> accounts(String connectionId) {
    return accounts.findByConnectionIdOrderByNameAsc(connectionId).stream()
        .map(e -> new ExternalBankAccount(e.getId(), e.getConnectionId(), e.getExternalAccountId(), e.getProductId(), e.getName(), e.getCurrency())).toList();
  }
  private BankConnection domain(BankConnectionEntity e) {
    return new BankConnection(e.getId(), e.getProvider(), e.getBankName(), e.getCountry(), e.getExternalSessionId(), e.getAuthorizationState(), e.getValidUntil(), e.getStatus(), e.getLastSyncAt());
  }
}
