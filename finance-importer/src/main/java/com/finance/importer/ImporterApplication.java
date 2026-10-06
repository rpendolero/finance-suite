package com.finance.importer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ImporterApplication {
  public static void main(String[] args) {
    try (var context = SpringApplication.run(ImporterApplication.class, args)) {
      /* Ejecución única; cierre de recursos al terminar. */
    }
  }
}
