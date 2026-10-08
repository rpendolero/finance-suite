package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class NonComputableMigrationTest {
  @Test
  void migratesExcludedMovementsAndRulesWithoutChangingNormalTransfers() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:noncomputable;MODE=MySQL")) {
      var statement = connection.createStatement();
      statement.execute("CREATE TABLE category (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(64), name VARCHAR(100), display_order INT)");
      statement.execute("CREATE TABLE subcategory (category_id BIGINT, code VARCHAR(64), name VARCHAR(100), display_order INT, active BOOLEAN DEFAULT TRUE)");
      for (String table : new String[] {"movement", "classification_rule"}) {
        statement.execute("CREATE TABLE " + table + " (id INT, category VARCHAR(64), subcategory VARCHAR(64), kind VARCHAR(24))");
        statement.execute("INSERT INTO " + table + " VALUES (1,'TRANSFERENCIAS',NULL,'CARD_SETTLEMENT'),(2,'TRANSFERENCIAS',NULL,'NORMAL'),(3,'OTROS',NULL,'DUPLICATE')");
      }
      try (var input = getClass().getResourceAsStream("/db/migration/V8__non_computable_categories.sql")) {
        String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
      }
      for (String table : new String[] {"movement", "classification_rule"}) {
        var rows = statement.executeQuery("SELECT category,subcategory,kind FROM " + table + " ORDER BY id");
        rows.next();
        assertThat(rows.getString(1)).isEqualTo("NO_COMPUTABLE");
        assertThat(rows.getString(2)).isEqualTo("LIQUIDACION_TARJETA");
        assertThat(rows.getString(3)).isEqualTo("CARD_SETTLEMENT");
        rows.next();
        assertThat(rows.getString(1)).isEqualTo("TRANSFERENCIAS");
        rows.next();
        assertThat(rows.getString(2)).isEqualTo("MOVIMIENTO_DUPLICADO");
      }
    }
  }
}
