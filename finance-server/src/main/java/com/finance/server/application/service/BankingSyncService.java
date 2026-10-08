package com.finance.server.application.service;

import com.finance.domain.Movement;
import com.finance.server.application.port.*;
import com.finance.server.domain.banking.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class BankingSyncService {
  private final BankingDataPort banking;
  private final BankConnectionPort connections;
  private final LedgerPort ledger;
  private final SettingsPort settings;
  private final MovementClassificationService classification;
  private final Clock clock;

  public List<BankConnection> connections() { return connections.findAll(); }

  public List<ExternalBankAccount> accounts(String connectionId) {
    requireConnection(connectionId);
    return connections.accounts(connectionId);
  }

  private BankConnection requireConnection(String id) {
    var connection = connections.find(id).orElseThrow(() -> new IllegalArgumentException("Bank connection not found: " + id));
    if (connection.status() != BankConnection.Status.ACTIVE || connection.externalSessionId() == null
        || connection.validUntil() == null || !connection.validUntil().isAfter(clock.instant()))
      throw new IllegalStateException("Bank connection requires authorization: " + id);
    return connection;
  }

  public SyncResult sync(String connectionId) {
    var connection = requireConnection(connectionId);

    int read = 0, inserted = 0;
    for (var link : connections.accounts(connectionId)) {
      if (link.productId() == null || link.productId().isBlank()) continue;
      ledger.product(link.productId()).orElseThrow(() -> new IllegalArgumentException("Product not found: " + link.productId()));
      var txs = banking.transactions(link.externalAccountId());
      var movements = txs.stream().map(tx -> toMovement(link.productId(), tx))
          .map(m -> classification.classify(m, settings.rules())).toList();
      read += movements.size();
      inserted += ledger.insert(movements);
    }
    connections.save(new BankConnection(connection.id(), connection.provider(), connection.bankName(), connection.country(), connection.externalSessionId(), connection.authorizationState(), connection.validUntil(), connection.status(), clock.instant()));
    log.info("Enable Banking synchronization completed: connectionId={}, read={}, inserted={}, duplicates={}", connectionId, read, inserted, read - inserted);
    return new SyncResult(read, inserted, read - inserted);
  }

  public List<ExternalBankAccount> discoverAccounts(String connectionId) {
    var connection = requireConnection(connectionId);
    var existing = connections.accounts(connectionId);
    return banking.accounts(connection.externalSessionId()).stream().map(a -> {
      var known = existing.stream().filter(x -> x.externalAccountId().equals(a.id())).findFirst();
      return known.orElseGet(() -> connections.saveAccount(new ExternalBankAccount(UUID.randomUUID().toString(), connectionId, a.id(), null, a.name(), a.currency())));
    }).toList();
  }

  public ExternalBankAccount link(String connectionId, String externalAccountId, String productId) {
    ledger.product(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    var account = connections.accounts(connectionId).stream().filter(a -> a.externalAccountId().equals(externalAccountId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("External account not found"));
    return connections.saveAccount(new ExternalBankAccount(account.id(), account.connectionId(), account.externalAccountId(), productId, account.name(), account.currency()));
  }

  private Movement toMovement(String productId, BankingDataPort.Transaction tx) {
    String externalId = tx.id();
    if (externalId == null || externalId.isBlank()) {
      String canonical = productId + "|" + tx.bookingDate() + "|" + tx.amount() + "|" + tx.currency() + "|" + normalize(tx.description());
      externalId = "eb-" + UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
    }
    return new Movement(UUID.randomUUID().toString(), productId, externalId, tx.bookingDate(), tx.amount().setScale(2), tx.currency(),
        tx.description() == null ? "" : tx.description(), tx.merchant(), "UNCLASSIFIED", Movement.Kind.NORMAL, tx.pending() ? Movement.Status.PENDING : Movement.Status.BOOKED);
  }
  private String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
  public record SyncResult(int read, int inserted, int duplicates) {}
}
