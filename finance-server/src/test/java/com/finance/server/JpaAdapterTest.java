package com.finance.server;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.*;
import com.finance.server.infrastructure.adapter.out.persistence.*;
import com.finance.server.infrastructure.adapter.out.persistence.mapper.PersistenceMapperImpl;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@org.springframework.context.annotation.Import({
  JpaLedgerAdapter.class,
  JpaSettingsAdapter.class,
  PersistenceMapperImpl.class
})
class JpaAdapterTest {
  @org.springframework.beans.factory.annotation.Autowired JpaLedgerAdapter ledger;
  @org.springframework.beans.factory.annotation.Autowired JpaSettingsAdapter settings;

  Product product(String id, Product.Provider provider) {
    return new Product(
        id,
        id,
        provider == Product.Provider.PAYPAL
            ? Product.ProductType.WALLET
            : Product.ProductType.ACCOUNT,
        "EUR",
        new BigDecimal("100.00"),
        Instant.parse("2026-10-02T06:00:00Z"),
        null,
        null,
        provider);
  }

  @Test
  void deletingMovementPreservesProductAndOtherMovements() {
    ledger.saveProduct(product("a", Product.Provider.ING));
    var first = new Movement(UUID.randomUUID().toString(), "a", "bank-first", LocalDate.parse("2026-09-01"), new BigDecimal("-10.00"), "EUR", "Compra", "Shop", "FOOD", Movement.Kind.NORMAL, Movement.Status.BOOKED);
    var second = new Movement(UUID.randomUUID().toString(), "a", "bank-second", first.date(), first.amount(), "EUR", "Compra", "Shop", "FOOD", Movement.Kind.NORMAL, Movement.Status.BOOKED);
    ledger.insert(List.of(first, second));
    ledger.deleteMovement(first.id());
    assertThat(ledger.movement(first.id())).isEmpty();
    assertThat(ledger.movement(second.id())).isPresent();
    assertThat(ledger.product("a")).isPresent();
    assertThatThrownBy(() -> ledger.deleteMovement(first.id())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bankBalanceUpdatePreservesProductAndRejectsWrongCurrency() {
    ledger.saveProduct(product("a", Product.Provider.ING));
    var at = Instant.parse("2026-10-08T10:00:00Z");
    ledger.updateBalance("a", new BigDecimal("-42.50"), "EUR", at);
    var saved = ledger.product("a").orElseThrow();
    assertThat(saved.balance()).isEqualByComparingTo("-42.50");
    assertThat(saved.balanceAt()).isEqualTo(at);
    assertThat(saved.name()).isEqualTo("a");
    assertThat(saved.provider()).isEqualTo(Product.Provider.ING);
    assertThatThrownBy(() -> ledger.updateBalance("a", BigDecimal.ZERO, "USD", at)).isInstanceOf(IllegalArgumentException.class);
    assertThat(ledger.product("a").orElseThrow().balance()).isEqualByComparingTo("-42.50");
  }

  @Test
  void persistsProvidersAndMoneders() {
    for (var provider : Product.Provider.values())
      ledger.saveProduct(product(provider.name(), provider));
    assertThat(ledger.products()).hasSize(3);
    assertThat(ledger.product("PAYPAL").orElseThrow().type()).isEqualTo(Product.ProductType.WALLET);
    ledger.saveProduct(product("ING", Product.Provider.ING));
    assertThat(ledger.products()).hasSize(3);
    assertThat(ledger.product("ING").orElseThrow().provider()).isEqualTo(Product.Provider.ING);
  }

  @Test
  void stableMovementIdentityPromotesPendingWithoutDuplication() {
    ledger.saveProduct(product("a", Product.Provider.ING));
    var m =
        new Movement(
            UUID.randomUUID().toString(),
            "a",
            "bank-id",
            LocalDate.parse("2026-09-01"),
            new BigDecimal("-10.00"),
            "EUR",
            "Compra",
            "Shop",
            "FOOD",
            Movement.Kind.NORMAL,
            Movement.Status.PENDING);
    assertThat(ledger.insert(List.of(m))).isOne();
    assertThat(ledger.insert(List.of(m))).isZero();
    var booked =
        new Movement(
            UUID.randomUUID().toString(),
            m.productId(),
            m.externalId(),
            m.date(),
            m.amount(),
            m.currency(),
            m.description(),
            m.merchant(),
            m.category(),
            m.kind(),
            Movement.Status.BOOKED);
    assertThat(ledger.insert(List.of(booked))).isZero();
    var period = new com.finance.domain.Period(m.date(), m.date());
    assertThat(ledger.movements(period, null)).hasSize(1);
    assertThat(ledger.movements(period, null).get(0).status()).isEqualTo(Movement.Status.BOOKED);
    ledger.classify(m.id(), "TRANSFERS", Movement.Kind.INTERNAL_TRANSFER);
    assertThat(ledger.movements(period, null).get(0).kind())
        .isEqualTo(Movement.Kind.INTERNAL_TRANSFER);
  }

  @Test
  void persistsRulesAndBudgets() {
    settings.saveRule(new ClassificationRule("shop", 10, "Shop", "FOOD", Movement.Kind.NORMAL));
    assertThat(settings.rules()).hasSize(1);
    settings.saveBudget(new Budget("2026-10", "FOOD", new BigDecimal("100.00")));
    settings.saveBudget(new Budget("2026-10", "FOOD", new BigDecimal("150.00")));
    assertThat(settings.budgets("2026-10").get(0).amount()).isEqualByComparingTo("150");
  }

  @Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void conflictingIdentityRollsBackTheWholeBatch() {
    String id = UUID.randomUUID().toString();
    ledger.saveProduct(product(id, Product.Provider.ING));
    var date = LocalDate.of(2026, 9, 1);
    var existing = movement(id, "existing", "-10.00", date);
    ledger.insert(List.of(existing));
    assertThatThrownBy(
            () ->
                ledger.insert(
                    List.of(
                        movement(id, "new", "-5.00", date),
                        movement(id, "existing", "-20.00", date))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ledger.movements(new com.finance.domain.Period(date, date), id))
        .extracting(Movement::externalId)
        .containsExactly("existing");
    ledger.deleteProduct(id);
    assertThat(ledger.product(id)).isEmpty();
    assertThat(ledger.movements(new com.finance.domain.Period(date, date), id)).isEmpty();
  }

  @Test
  void twoCardsFromTheSameBankKeepSeparateMovementsAndIdentity() {
    ledger.saveProduct(product("shared-account", Product.Provider.KUTXABANK));
    ledger.saveProduct(card("card-a", "bank-card-a", "**** 1234"));
    ledger.saveProduct(card("card-b", "bank-card-b", "**** 1234"));
    var date = LocalDate.of(2026, 10, 1);
    var first = movement("card-a", "same-bank-id", "-10.00", date);
    var second = movement("card-b", "same-bank-id", "-10.00", date);
    assertThat(ledger.insert(List.of(first, second))).isEqualTo(2);
    assertThat(ledger.insert(List.of(first, second))).isZero();
    var period = new com.finance.domain.Period(date, date);
    assertThat(ledger.movements(period, "card-a")).extracting(Movement::productId)
        .containsExactly("card-a");
    assertThat(ledger.movements(period, "card-b")).extracting(Movement::productId)
        .containsExactly("card-b");
    assertThat(ledger.product("card-a").orElseThrow().externalId()).isEqualTo("bank-card-a");
    assertThat(ledger.product("card-b").orElseThrow().maskedPan()).isEqualTo("**** 1234");
  }

  @Test
  void olderSnapshotsKeepCardMetadataAndDifferentReferenceIsRejected() {
    ledger.saveProduct(product("shared-account", Product.Provider.KUTXABANK));
    ledger.saveProduct(card("card-a", "bank-card-a", "**** 1234"));
    ledger.saveProduct(card("card-a", null, null));
    assertThat(ledger.product("card-a").orElseThrow().externalId()).isEqualTo("bank-card-a");
    assertThat(ledger.product("card-a").orElseThrow().maskedPan()).isEqualTo("**** 1234");
    assertThatThrownBy(() -> ledger.saveProduct(card("card-a", "bank-card-b", "**** 5678")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void linkedAccountMustBelongToSameBank() {
    ledger.saveProduct(product("shared-account", Product.Provider.ING));
    assertThatThrownBy(() -> ledger.saveProduct(card("card-a", "bank-card-a", "**** 1234")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sameBankReferenceCannotBeRegisteredTwice() {
    ledger.saveProduct(product("shared-account", Product.Provider.KUTXABANK));
    ledger.saveProduct(card("card-a", "bank-card-a", "**** 1234"));
    assertThatThrownBy(() -> ledger.saveProduct(card("card-b", "bank-card-a", "**** 1234")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fullPanAndCardMetadataOnAccountsAreRejected() {
    assertThatThrownBy(() -> card("card-a", null, "1234567890123456"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Product("account", "Account", Product.ProductType.ACCOUNT,
        "EUR", BigDecimal.ZERO, Instant.now(), null, null, Product.Provider.ING,
        null, "**** 1234")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void duplicateWithinOneBatchIsCountedOnceAndPromotesPending() {
    ledger.saveProduct(product("batch-account", Product.Provider.ING));
    var date = LocalDate.of(2026, 10, 1);
    var booked = movement("batch-account", "same-reference", "-10.00", date);
    var pending = new Movement(UUID.randomUUID().toString(), booked.productId(),
        booked.externalId(), booked.date(), booked.amount(), booked.currency(),
        booked.description(), booked.merchant(), booked.category(), booked.kind(), Movement.Status.PENDING);
    assertThat(ledger.insert(List.of(pending, booked))).isOne();
    assertThat(ledger.movements(new com.finance.domain.Period(date, date), "batch-account"))
        .singleElement().extracting(Movement::status).isEqualTo(Movement.Status.BOOKED);
  }

  @Test
  void repositoryQueriesPreserveInclusiveDatesAndOrdering() {
    ledger.saveProduct(product("range-account", Product.Provider.ING));
    var from = LocalDate.of(2026, 10, 1);
    var to = from.plusDays(2);
    ledger.insert(List.of(movement("range-account", "last", "-1.00", to),
        movement("range-account", "first", "-1.00", from),
        movement("range-account", "outside", "-1.00", to.plusDays(1))));
    var period = new com.finance.domain.Period(from, to);
    assertThat(ledger.movements(period, "range-account")).extracting(Movement::externalId)
        .containsExactly("first", "last");
    assertThat(ledger.movements(period, "")).extracting(Movement::externalId)
        .containsExactly("first", "last");
  }

  private Product card(String id, String externalId, String maskedPan) {
    return new Product(id, id, Product.ProductType.CREDIT_CARD, "EUR",
        new BigDecimal("-100.00"), Instant.parse("2026-10-02T06:00:00Z"),
        "shared-account", new BigDecimal("2000.00"), Product.Provider.KUTXABANK,
        externalId, maskedPan);
  }

  private Movement movement(String productId, String externalId, String amount, LocalDate date) {
    return new Movement(
        UUID.randomUUID().toString(),
        productId,
        externalId,
        date,
        new BigDecimal(amount),
        "EUR",
        "Test purchase",
        "Test merchant",
        "FOOD",
        Movement.Kind.NORMAL,
        Movement.Status.BOOKED);
  }
}
