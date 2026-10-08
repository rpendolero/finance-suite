package com.finance.server.application.service;

import com.finance.domain.Movement;
import com.finance.domain.Product;
import com.finance.server.application.port.BankingDataPort;
import com.finance.server.application.port.BankConnectionPort;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.domain.banking.BankConnection;
import com.finance.server.domain.banking.ExternalBankAccount;
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

    int read = 0, inserted = 0, balancesUpdated = 0, balancesSkipped = 0;
    for (var link : connections.accounts(connectionId)) {
      if (link.productId() == null || link.productId().isBlank()) continue;
      Product product = ledger.product(link.productId()).orElseThrow(() -> new IllegalArgumentException("Product not found: " + link.productId()));
      validateProduct(link, product);
      var balance = selectBestBalance(link.externalAccountId(), product);
      var movements = fetchAndClassifyMovements(link.externalAccountId(), link.productId());
      read += movements.size();
      inserted += ledger.insert(movements);
      var balanceResult = updateProductBalance(product, balance);
      balancesUpdated += balanceResult.updated();
      balancesSkipped += balanceResult.skipped();
    }
    connections.save(new BankConnection(connection.id(), connection.provider(), connection.bankName(), connection.country(), connection.externalSessionId(), connection.authorizationState(), connection.validUntil(), connection.status(), clock.instant()));
    log.info("Enable Banking synchronization completed: connectionId={}, read={}, inserted={}, duplicates={}, balancesUpdated={}, balancesSkipped={}", connectionId, read, inserted, read - inserted, balancesUpdated, balancesSkipped);
    return new SyncResult(read, inserted, read - inserted, balancesUpdated, balancesSkipped);
  }

  private java.util.Optional<BankingDataPort.Balance> selectBestBalance(String externalAccountId, Product product) {
    boolean card = product.type() == Product.ProductType.CREDIT_CARD || product.type() == Product.ProductType.DEBIT_CARD;
    List<String> balanceTypes = card ? List.of("ITBD", "CLBD") : List.of("ITAV", "CLAV", "ITBD", "CLBD");
    return banking.balanceSnapshots(externalAccountId).stream()
        .filter(b -> product.currency().equals(b.currency()) && b.type() != null && balanceTypes.contains(b.type()))
        .min(Comparator.comparingInt((BankingDataPort.Balance b) -> balanceTypes.indexOf(b.type()))
            .thenComparing(BankingDataPort.Balance::at, Comparator.nullsLast(Comparator.reverseOrder())));
  }

  private List<Movement> fetchAndClassifyMovements(String externalAccountId, String productId) {
    var txs = banking.transactions(externalAccountId);
    return txs.stream()
        .map(tx -> classification.classify(toMovement(productId, tx), settings.rules()))
        .toList();
  }

  private BalanceUpdateResult updateProductBalance(Product product, Optional<BankingDataPort.Balance> balance) {
    if (balance.isPresent()) {
      var snapshot = balance.get();
      ledger.updateBalance(product.id(), snapshot.amount(), snapshot.currency(), snapshot.at() == null ? clock.instant() : snapshot.at());
      return new BalanceUpdateResult(1, 0);
    } else {
      log.warn("No suitable bank balance returned: productId={}", product.id());
      return new BalanceUpdateResult(0, 1);
    }
  }

  private record BalanceUpdateResult(int updated, int skipped) {}

  public List<ExternalBankAccount> discoverAccounts(String connectionId) {
    var connection = requireConnection(connectionId);
    var existing = connections.accounts(connectionId);
    return banking.accounts(connection.externalSessionId()).stream().map(a -> {
      var known = existing.stream().filter(x -> x.externalAccountId().equals(a.id())).findFirst();
      return connections.saveAccount(new ExternalBankAccount(
          known.map(ExternalBankAccount::id).orElseGet(() -> UUID.randomUUID().toString()),
          connectionId, a.id(), known.map(ExternalBankAccount::productId).orElse(null),
          a.name(), a.currency(), a.cashAccountType()));
    }).toList();
  }

  public ExternalBankAccount link(String connectionId, String externalAccountId, String productId) {
    Product product = ledger.product(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    var account = connections.accounts(connectionId).stream().filter(a -> a.externalAccountId().equals(externalAccountId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("External account not found"));
    validateProduct(account, product);
    return connections.saveAccount(new ExternalBankAccount(account.id(), account.connectionId(), account.externalAccountId(), productId, account.name(), account.currency(), account.cashAccountType()));
  }

  private void validateProduct(ExternalBankAccount account, Product product) {
    if (!"XXX".equals(account.currency()) && !account.currency().equals(product.currency()))
      throw new IllegalArgumentException("La moneda de la cuenta y el producto debe coincidir");
    boolean card = product.type() == Product.ProductType.CREDIT_CARD || product.type() == Product.ProductType.DEBIT_CARD;
    if ("CARD".equals(account.cashAccountType()) && !card)
      throw new IllegalArgumentException("Vincula esta cuenta CARD a un producto de tarjeta");
    if (("CACC".equals(account.cashAccountType()) || "SVGS".equals(account.cashAccountType()))
        && product.type() != Product.ProductType.ACCOUNT)
      throw new IllegalArgumentException("Vincula esta cuenta bancaria a un producto de cuenta");
  }

  private Movement toMovement(String productId, BankingDataPort.Transaction tx) {
    log.debug("Enable Banking transaction {} [{}] [{}] mapping started: productId={}", tx.id(), tx.bookingDate(), tx.description(), productId);
    String externalId = tx.id();
    if (externalId == null || externalId.isBlank()) {
      String canonical = productId + "|" + tx.bookingDate() + "|" + tx.amount() + "|" + tx.currency() + "|" + normalize(tx.description());
      externalId = "eb-" + UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
    }
    try {
      return new Movement(UUID.randomUUID().toString(), productId, externalId, tx.bookingDate(), tx.amount().setScale(2), tx.currency(),
              tx.description() == null ? "" : tx.description(), tx.merchant(), "UNCLASSIFIED", Movement.Kind.NORMAL, tx.pending() ? Movement.Status.PENDING : Movement.Status.BOOKED);

    } catch (Exception e) {
      log.error("Enable Banking transaction {} [{}] [{}] mapping failed: errorType={}", tx.id(), tx.bookingDate(), tx.description(), e.getClass().getSimpleName(), e);
    }
    return null;
  }

  private String normalize(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
  public record SyncResult(int read, int inserted, int duplicates, int balancesUpdated, int balancesSkipped) {
    public SyncResult(int read, int inserted, int duplicates) { this(read, inserted, duplicates, 0, 0); }
  }
}
