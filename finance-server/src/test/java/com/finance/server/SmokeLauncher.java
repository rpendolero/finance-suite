package com.finance.server;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finance.domain.*;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.infrastructure.adapter.out.csv.CsvStatementAdapter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Servidor con datos ficticios para probar el transporte MCP de extremo a extremo. */
public class SmokeLauncher {
  public static void main(String[] args) {
    var app = new SpringApplication(FinanceApplication.class, Fixture.class);
    var properties =
        Map.of(
            "spring.datasource.url",
            "jdbc:h2:mem:transport;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.password",
            "",
            "spring.flyway.enabled",
            "false",
            "spring.jpa.hibernate.ddl-auto",
            "none",
            "finance.security.reader-password",
            "reader-secret-for-test-12345",
            "finance.security.admin-password",
            "admin-secret-for-test-67890",
            "finance.security.importer-password",
            "importer-secret-for-test-54321",
            "server.port",
            "18081");
    app.run(
        properties.entrySet().stream()
            .map(e -> "--" + e.getKey() + "=" + e.getValue())
            .toArray(String[]::new));
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    @Primary
    LedgerPort fixtureLedger() throws Exception {
      var ledger = mock(LedgerPort.class);
      var parser = new CsvStatementAdapter();
      var movements = new ArrayList<Movement>();
      try (var a = Files.newInputStream(Path.of("examples/account.csv"));
          var c = Files.newInputStream(Path.of("examples/card.csv"))) {
        movements.addAll(parser.parse(a, "account-main"));
        movements.addAll(parser.parse(c, "card-main"));
      }
      when(ledger.products())
          .thenReturn(
              List.of(
                  new Product(
                      "account-main",
                      "Account",
                      Product.ProductType.ACCOUNT,
                      "EUR",
                      new BigDecimal("5000.00"),
                      Instant.now(),
                      null,
                      null),
                  new Product(
                      "card-main",
                      "Card",
                      Product.ProductType.CREDIT_CARD,
                      "EUR",
                      new BigDecimal("-150.00"),
                      Instant.now(),
                      "account-main",
                      new BigDecimal("2000.00"))));
      when(ledger.movements(any(com.finance.domain.Period.class), nullable(String.class)))
          .thenAnswer(
              inv -> {
                com.finance.domain.Period p = inv.getArgument(0);
                String id = inv.getArgument(1);
                return movements.stream()
                    .filter(
                        m ->
                            !m.date().isBefore(p.from())
                                && !m.date().isAfter(p.to())
                                && (id == null || id.isBlank() || id.equals(m.productId())))
                    .toList();
              });
      return ledger;
    }

    @Bean
    @Primary
    SettingsPort fixtureSettings() {
      return mock(SettingsPort.class);
    }
  }
}
