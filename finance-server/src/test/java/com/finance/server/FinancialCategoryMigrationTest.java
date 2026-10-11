package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class FinancialCategoryMigrationTest {

  @Test
  void migratesOnlyUnambiguousHistoryAndKeepsManualMetadata() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:financial_categories;MODE=MySQL")) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE category (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
            + "code VARCHAR(64) UNIQUE, name VARCHAR(100), active BOOLEAN DEFAULT TRUE, display_order INT)");
        statement.execute("CREATE TABLE subcategory (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
            + "category_id BIGINT, code VARCHAR(64), name VARCHAR(100), "
            + "active BOOLEAN DEFAULT TRUE, display_order INT, UNIQUE(category_id,code))");
        statement.execute("CREATE TABLE movement (id INT PRIMARY KEY, "
            + "category VARCHAR(64), subcategory VARCHAR(64), "
            + "kind VARCHAR(32), classification_source VARCHAR(32), classification_confidence DECIMAL(5,4))");
        statement.execute("CREATE TABLE classification_rule (id VARCHAR(64) PRIMARY KEY, "
            + "category VARCHAR(64), subcategory VARCHAR(64), kind VARCHAR(32))");

        String[] existing = {"INGRESOS", "VIVIENDA", "TRANSPORTE", "OCIO", "SUSCRIPCIONES",
            "IMPUESTOS", "OTROS", "COMPRAS", "HOGAR", "FINANCIACION", "AHORRO_E_INVERSION"};
        for (String code : existing) {
          statement.execute("INSERT INTO category (code,name,active,display_order) "
              + "VALUES ('" + code + "','" + code + "',TRUE,1)");
        }
        String[][] legacy = {
            {"OCIO","REGALOS"}, {"VIVIENDA","HIPOTECA"},
            {"VIVIENDA","HIPOTECA_ALQUILER"}, {"TRANSPORTE","FINANCIACION_VEHICULO"},
            {"SUSCRIPCIONES","TELEFONIA"}, {"IMPUESTOS","MULTAS"},
            {"HOGAR","SERVICIO_DOMESTICO"}, {"COMPRAS","ONLINE"}};
        for (String[] row : legacy) {
          statement.execute("INSERT INTO subcategory (category_id,code,name,active,display_order) "
              + "SELECT id,'" + row[1] + "','" + row[1] + "',TRUE,1 FROM category WHERE code='" + row[0] + "'");
        }
        statement.execute("INSERT INTO movement VALUES "
            + "(1,'OCIO','REGALOS','NORMAL','MANUAL',1.0000),"
            + "(2,'VIVIENDA','HIPOTECA','NORMAL','MANUAL',1.0000),"
            + "(3,'VIVIENDA','HIPOTECA_ALQUILER','NORMAL','MANUAL',1.0000),"
            + "(4,'COMPRAS','ONLINE','NORMAL','MANUAL',1.0000),"
            + "(5,'TRANSPORTE','FINANCIACION_VEHICULO','NORMAL','MANUAL',1.0000),"
            + "(6,'HOGAR','SERVICIO_DOMESTICO','NORMAL','MANUAL',1.0000)");
        statement.execute("INSERT INTO classification_rule VALUES "
            + "('gift','OCIO','REGALOS','NORMAL'),"
            + "('loan','TRANSPORTE','FINANCIACION_VEHICULO','NORMAL')");

        executeMigration(statement);
        executeMigration(statement);

        assertThat(select(statement, "SELECT category FROM movement WHERE id=1")).isEqualTo("COMPRAS");
        assertThat(select(statement, "SELECT category FROM movement WHERE id=2")).isEqualTo("FINANCIACION");
        assertThat(select(statement, "SELECT category FROM movement WHERE id=3")).isEqualTo("VIVIENDA");
        assertThat(select(statement, "SELECT category FROM movement WHERE id=4")).isEqualTo("COMPRAS");
        assertThat(select(statement, "SELECT category FROM movement WHERE id=5")).isEqualTo("FINANCIACION");
        assertThat(select(statement, "SELECT subcategory FROM movement WHERE id=5")).isEqualTo("VEHICULO");
        assertThat(select(statement, "SELECT category FROM movement WHERE id=6")).isEqualTo("VIVIENDA");
        assertThat(select(statement, "SELECT classification_source FROM movement WHERE id=1")).isEqualTo("MANUAL");
        assertThat(select(statement, "SELECT classification_confidence FROM movement WHERE id=1")).isEqualTo("1.0000");
        assertThat(select(statement, "SELECT category FROM classification_rule WHERE id='gift'")).isEqualTo("COMPRAS");
        assertThat(select(statement, "SELECT subcategory FROM classification_rule WHERE id='loan'")).isEqualTo("VEHICULO");
        assertThat(select(statement, "SELECT active FROM category WHERE code='HOGAR'")).isEqualTo("FALSE");
        assertThat(select(statement, "SELECT s.active FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='OCIO' AND s.code='REGALOS'")).isEqualTo("FALSE");
        assertThat(select(statement, "SELECT COUNT(*) FROM subcategory s JOIN category c ON c.id=s.category_id "
            + "WHERE c.code='FINANCIACION' AND s.code='HIPOTECA'")).isEqualTo("1");
      }
    }
  }

  private void executeMigration(Statement statement) throws Exception {
    try (var input = getClass().getResourceAsStream(
        "/db/migration/V11__financial_category_normalization.sql")) {
      assertThat(input).isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("(?m)^--[^\\r\\n]*", "");
      for (String command : sql.split(";")) {
        if (!command.isBlank()) statement.execute(command);
      }
    }
  }

  private String select(Statement statement, String query) throws Exception {
    try (ResultSet rs = statement.executeQuery(query)) {
      assertThat(rs.next()).isTrue();
      return rs.getString(1);
    }
  }
}
