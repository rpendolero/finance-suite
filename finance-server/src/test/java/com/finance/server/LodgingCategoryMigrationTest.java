package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class LodgingCategoryMigrationTest {

  @Test
  void relocatesHotelSubcategoriesAndHistoricalClassificationsWithoutAffectingFood() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:lodging_categories;MODE=MySQL")) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE category (id BIGINT PRIMARY KEY, code VARCHAR(64), name VARCHAR(100))");
        statement.execute("CREATE TABLE subcategory (id BIGINT AUTO_INCREMENT PRIMARY KEY, category_id BIGINT, code VARCHAR(64), name VARCHAR(100), active BOOLEAN DEFAULT TRUE, display_order INT, UNIQUE(category_id, code))");
        statement.execute("CREATE TABLE movement (id INT PRIMARY KEY, category VARCHAR(64), subcategory VARCHAR(64), classification_source VARCHAR(24))");
        statement.execute("CREATE TABLE classification_rule (id VARCHAR(40) PRIMARY KEY, category VARCHAR(64), subcategory VARCHAR(64))");
        statement.execute("INSERT INTO category VALUES (1, 'ALIMENTACION', 'Alimentación'), (2, 'OCIO', 'Ocio')");
        statement.execute("INSERT INTO subcategory (category_id, code, name, active, display_order) VALUES "
            + "(1, 'HOTELES', 'Hoteles', TRUE, 40), (1, 'ALOJAMIENTO', 'Alojamiento', TRUE, 50), "
            + "(1, 'SUPERMERCADO', 'Supermercado', TRUE, 10), "
            + "(2, 'HOTEL', 'Hotel', FALSE, 40), (2, 'VIAJES', 'Viajes', TRUE, 10)");
        statement.execute("INSERT INTO movement VALUES "
            + "(1, 'ALIMENTACION', 'HOTELES', 'MANUAL'), "
            + "(2, 'ALIMENTACION', 'ALOJAMIENTO', 'MERCHANT_RULE'), "
            + "(3, 'ALIMENTACION', 'SUPERMERCADO', 'AUTOMATIC'), "
            + "(4, 'OCIO', 'VIAJES', 'MANUAL')");
        statement.execute("INSERT INTO classification_rule VALUES "
            + "('lodging', 'ALIMENTACION', 'HOTELES'), "
            + "('supermarket', 'ALIMENTACION', 'SUPERMERCADO')");
        runMigration(statement);
        runMigration(statement);

        assertThat(value(statement, "SELECT category FROM movement WHERE id = 1")).isEqualTo("OCIO");
        assertThat(value(statement, "SELECT classification_source FROM movement WHERE id = 1")).isEqualTo("MANUAL");
        assertThat(value(statement, "SELECT category FROM movement WHERE id = 2")).isEqualTo("OCIO");
        assertThat(value(statement, "SELECT category FROM movement WHERE id = 3")).isEqualTo("ALIMENTACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id = 4")).isEqualTo("OCIO");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id = 'lodging'")).isEqualTo("OCIO");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id = 'supermarket'")).isEqualTo("ALIMENTACION");
        assertThat(value(statement, "SELECT active FROM subcategory WHERE category_id = 1 AND code = 'HOTELES'")).isEqualTo("FALSE");
        assertThat(value(statement, "SELECT active FROM subcategory WHERE category_id = 2 AND code = 'HOTELES'")).isEqualTo("TRUE");
        assertThat(value(statement, "SELECT active FROM subcategory WHERE category_id = 2 AND code = 'ALOJAMIENTO'")).isEqualTo("TRUE");
        assertThat(value(statement, "SELECT active FROM subcategory WHERE category_id = 2 AND code = 'HOTEL'")).isEqualTo("TRUE");
        assertThat(value(statement, "SELECT COUNT(*) FROM subcategory WHERE category_id = 2 AND code = 'HOTELES'")).isEqualTo("1");
      }
    }
  }

  private void runMigration(Statement statement) throws Exception {
    try (var resource = getClass().getResourceAsStream("/db/migration/V10__lodging_category_alignment.sql")) {
      assertThat(resource).isNotNull();
      String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      for (String command : sql.split(";")) {
        if (!command.isBlank()) statement.execute(command);
      }
    }
  }

  private String value(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }
}
