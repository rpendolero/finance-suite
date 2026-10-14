package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class RestaurantCategoryMigrationTest {

  @Test
  void movesRestaurantHistoryAndRulesWithoutTouchingGroceriesOrManualMetadata() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:restaurant_categories;MODE=MySQL")) {
      try (Statement statement = connection.createStatement()) {
        createSchema(statement);
        statement.execute("INSERT INTO category (id, code, name, active, display_order) VALUES "
            + "(1, 'ALIMENTACION', 'Alimentación', TRUE, 30), "
            + "(2, 'OCIO', 'Ocio', TRUE, 50)");
        statement.execute("INSERT INTO subcategory (id, category_id, code, name, active, display_order) VALUES "
            + "(11, 1, 'SUPERMERCADO', 'Supermercado', TRUE, 10), "
            + "(12, 1, 'RESTAURANTES', 'Restaurantes', TRUE, 20), "
            + "(13, 1, 'COMIDA_DOMICILIO', 'Comida a domicilio', TRUE, 30), "
            + "(14, 2, 'HOTELES', 'Hoteles', TRUE, 50), "
            + "(15, 1, 'RESTAURANTE', 'Restaurante', TRUE, 21)");
        statement.execute("INSERT INTO movement VALUES "
            + "(1, 'ALIMENTACION', 'RESTAURANTES', -45.00, 'NORMAL', 'MANUAL', 1.0000), "
            + "(2, 'ALIMENTACION', 'COMIDA_DOMICILIO', -20.00, 'NORMAL', 'MERCHANT_RULE', 0.9900), "
            + "(3, 'ALIMENTACION', 'SUPERMERCADO', -65.00, 'NORMAL', 'MANUAL', 1.0000), "
            + "(4, 'OCIO', 'HOTELES', -100.00, 'NORMAL', 'MANUAL', 1.0000), "
            + "(5, 'ALIMENTACION', NULL, -8.00, 'NORMAL', 'MANUAL', 1.0000), "
            + "(6, 'UNCLASSIFIED', 'RESTAURANTES', -5.00, 'NORMAL', 'UNCLASSIFIED', 0.0000), "
            + "(7, 'ALIMENTACION', 'RESTAURANTE', -18.00, 'NORMAL', 'MANUAL', 1.0000)");
        statement.execute("INSERT INTO classification_rule VALUES "
            + "('restaurant', 'ALIMENTACION', 'RESTAURANTES'), "
            + "('delivery', 'ALIMENTACION', 'COMIDA_DOMICILIO'), "
            + "('market', 'ALIMENTACION', 'SUPERMERCADO'), "
            + "('restaurant_singular', 'ALIMENTACION', 'RESTAURANTE')");

        runMigration(statement);
        runMigration(statement);

        assertThat(value(statement, "SELECT name FROM category WHERE code='RESTAURACION'"))
            .isEqualTo("Restauración");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=1"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=2"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=7"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=3"))
            .isEqualTo("ALIMENTACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=4"))
            .isEqualTo("OCIO");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=5"))
            .isEqualTo("ALIMENTACION");
        assertThat(value(statement, "SELECT category FROM movement WHERE id=6"))
            .isEqualTo("UNCLASSIFIED");
        assertThat(value(statement, "SELECT classification_source FROM movement WHERE id=1"))
            .isEqualTo("MANUAL");
        assertThat(value(statement, "SELECT classification_confidence FROM movement WHERE id=1"))
            .isEqualTo("1.0000");
        assertThat(value(statement, "SELECT amount FROM movement WHERE id=1"))
            .isEqualTo("-45.00");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id='restaurant'"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id='delivery'"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id='restaurant_singular'"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT category FROM classification_rule WHERE id='market'"))
            .isEqualTo("ALIMENTACION");
        assertThat(value(statement, "SELECT c.code FROM subcategory s JOIN category c ON c.id=s.category_id WHERE s.id=12"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT c.code FROM subcategory s JOIN category c ON c.id=s.category_id WHERE s.id=15"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT c.code FROM subcategory s JOIN category c ON c.id=s.category_id WHERE s.id=13"))
            .isEqualTo("RESTAURACION");
        assertThat(value(statement, "SELECT COUNT(*) FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='ALIMENTACION' AND s.code IN ('RESTAURANTE','RESTAURANTES','COMIDA_DOMICILIO')"))
            .isEqualTo("0");
        assertThat(value(statement, "SELECT COUNT(*) FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='RESTAURACION' AND s.code IN ('RESTAURANTE','RESTAURANTES','BARES_CAFETERIAS','COMIDA_DOMICILIO')"))
            .isEqualTo("4");
        assertThat(value(statement, "SELECT s.name FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='RESTAURACION' AND s.code='BARES_CAFETERIAS'"))
            .isEqualTo("Bares y cafeterías");
        assertThat(value(statement, "SELECT s.name FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='ALIMENTACION' AND s.code='OTROS_ALIMENTOS'"))
            .isEqualTo("Otros alimentos");
      }
    }
  }

  @Test
  void reactivatesPreviouslyCreatedRestorationCategory() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:restaurant_existing;MODE=MySQL")) {
      try (Statement statement = connection.createStatement()) {
        createSchema(statement);
        statement.execute("INSERT INTO category (id, code, name, active, display_order) VALUES "
            + "(1, 'ALIMENTACION', 'Alimentación', TRUE, 30), "
            + "(2, 'RESTAURACION', 'Restauracion antigua', FALSE, 99)");
        statement.execute("INSERT INTO subcategory (id, category_id, code, name, active, display_order) VALUES "
            + "(10, 1, 'RESTAURANTES', 'Restaurantes', TRUE, 20)");
        runMigration(statement);
        assertThat(value(statement, "SELECT active FROM category WHERE code='RESTAURACION'")).isEqualTo("TRUE");
        assertThat(value(statement, "SELECT name FROM category WHERE code='RESTAURACION'")).isEqualTo("Restauración");
        assertThat(value(statement, "SELECT c.code FROM subcategory s JOIN category c ON c.id=s.category_id WHERE s.id=10"))
            .isEqualTo("RESTAURACION");
      }
    }
  }

  private void createSchema(Statement statement) throws Exception {
    statement.execute("CREATE TABLE category (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(64) UNIQUE, "
        + "name VARCHAR(100), active BOOLEAN DEFAULT TRUE, display_order INT)");
    statement.execute("CREATE TABLE subcategory (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
        + "category_id BIGINT REFERENCES category(id), code VARCHAR(64), name VARCHAR(100), "
        + "active BOOLEAN DEFAULT TRUE, display_order INT, UNIQUE(category_id, code))");
    statement.execute("CREATE TABLE movement (id INT PRIMARY KEY, category VARCHAR(64), "
        + "subcategory VARCHAR(64), amount DECIMAL(18,2), kind VARCHAR(24), "
        + "classification_source VARCHAR(24), classification_confidence DECIMAL(5,4))");
    statement.execute("CREATE TABLE classification_rule (id VARCHAR(64) PRIMARY KEY, "
        + "category VARCHAR(64), subcategory VARCHAR(64))");
  }

  private void runMigration(Statement statement) throws Exception {
    try (var resource = getClass().getResourceAsStream(
        "/db/migration/V12__restaurant_category_separation.sql")) {
      assertThat(resource).isNotNull();
      String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("(?m)^--[^\\r\\n]*", "");
      for (String command : sql.split(";")) {
        if (!command.isBlank()) statement.execute(command);
      }
    }
  }

  private String value(Statement statement, String sql) throws Exception {
    try (ResultSet rs = statement.executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getString(1);
    }
  }
}
