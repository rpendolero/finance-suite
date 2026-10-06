package com.finance.server;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.*;
import com.finance.server.infrastructure.adapter.out.persistence.*;
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
