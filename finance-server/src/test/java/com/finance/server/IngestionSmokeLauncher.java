package com.finance.server;

import java.util.Map;
import org.springframework.boot.SpringApplication;

/** Servidor real con H2 y datos ficticios para comprobar los dos procesos independientes. */
public class IngestionSmokeLauncher {
  public static void main(String[] args) {
    var properties =
        Map.of(
            "spring.datasource.url", "jdbc:h2:mem:two-process;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.password", "",
            "spring.flyway.enabled", "false",
            "spring.jpa.hibernate.ddl-auto", "none",
            "spring.sql.init.mode", "always",
            "spring.sql.init.schema-locations", "classpath:ingestion-schema.sql",
            "finance.security.reader-password", "reader-secret-for-test-12345",
            "finance.security.admin-password", "admin-secret-for-test-67890",
            "finance.security.importer-password", "importer-secret-for-test-54321",
            "server.port", "18081");
    new SpringApplication(FinanceApplication.class)
        .run(
            properties.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new));
  }
}
