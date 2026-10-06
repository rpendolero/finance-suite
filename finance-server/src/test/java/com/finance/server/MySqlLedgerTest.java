package com.finance.server;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.*;
import com.finance.server.infrastructure.adapter.out.persistence.JpaLedgerAdapter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@org.springframework.boot.test.context.SpringBootTest(
    properties = {
      "finance.security.reader-password=reader-secret-for-test-12345",
      "finance.security.admin-password=admin-secret-for-test-67890",
      "finance.security.importer-password=importer-secret-for-test-54321"
    })
@Testcontainers(disabledWithoutDocker = true)
class MySqlLedgerTest {
  @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @org.springframework.beans.factory.annotation.Autowired JpaLedgerAdapter adapter;

  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Test
  void migrationsAndIdempotentImports() {
    adapter.saveProduct(
        new Product(
            "a",
            "Account",
            Product.ProductType.ACCOUNT,
            "EUR",
            new BigDecimal("100.00"),
            Instant.now(),
            null,
            null));
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
            Movement.Status.BOOKED);
    assertThat(adapter.insert(List.of(m))).isEqualTo(1);
    assertThat(adapter.insert(List.of(m))).isZero();
    assertThat(adapter.products()).hasSize(1);
    assertThat(adapter.movements(new com.finance.domain.Period(m.date(), m.date()), null))
        .hasSize(1);
  }
}
