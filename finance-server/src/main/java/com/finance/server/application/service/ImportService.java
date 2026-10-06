package com.finance.server.application.service;

import com.finance.server.application.port.*;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Orchestrates validation, parsing, classification and persistence through ports. */
@RequiredArgsConstructor
@Slf4j
public final class ImportService {
  private final SettingsPort settings;
  private final LedgerPort ledger;
  private final StatementParserPort parser;
  private final MovementClassificationService classification;

  public ImportService(SettingsPort settings, LedgerPort ledger, StatementParserPort parser) {
    this(settings, ledger, parser, new MovementClassificationService());
  }

  public record Result(int read, int inserted, int duplicates) {}

  public Result importCsv(InputStream input, String productId) {
    log.info("Statement processing started");
    requireRegisteredProduct(productId);
    var rules = settings.rules();
    var movements =
        parser.parse(input, productId).stream()
            .map(movement -> classification.classify(movement, rules))
            .toList();
    log.debug("Statement parsed and classified: rows={}", movements.size());
    int inserted = ledger.insert(movements);
    log.info(
        "Statement processed: read={}, inserted={}, duplicates={}",
        movements.size(),
        inserted,
        movements.size() - inserted);
    return new Result(movements.size(), inserted, movements.size() - inserted);
  }

  private void requireRegisteredProduct(String productId) {
    if (ledger.product(productId).isEmpty())
      throw new IllegalArgumentException("Registra el producto antes de importar");
  }
}
