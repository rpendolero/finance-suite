package com.finance.server.application.service;

import com.finance.domain.Product;
import com.finance.server.application.port.*;
import java.io.InputStream;
import java.util.Objects;
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
  private final UnitOfWorkPort unitOfWork;

  public ImportService(
          SettingsPort settings,
          LedgerPort ledger,
          StatementParserPort parser) {
    this(
        settings,
        ledger,
        parser,
        new MovementClassificationService(),
        new DirectUnitOfWork());
  }

  public ImportService(
      SettingsPort settings,
      LedgerPort ledger,
      StatementParserPort parser,
      MovementClassificationService classification) {
    this(settings, ledger, parser, classification, new DirectUnitOfWork());
  }

  public record Result(int read, int inserted, int duplicates) {}

  /**
   * Imports a statement optionally updating the product snapshot first.
   */
  public Result importBatch(
          InputStream input,
          String productId,
          Product snapshot) {

    return unitOfWork.execute(
        () -> {
          if (snapshot != null) {
            validateAndSaveProduct(productId, snapshot);
          }
          return importCsv(input, productId);
        });
  }

  /**
   * Imports movements for an already registered product.
   */
  public Result importCsv(
          InputStream input,
          String productId) {

    log.info(
            "Statement processing started: productId={}",
            productId);

    requireRegisteredProduct(productId);

    var rules = settings.rules();

    var movements =
            parser.parse(input, productId).stream()
                    .map(movement ->
                            classification.classify(movement, rules))
                    .toList();

    log.debug(
            "Statement parsed and classified: productId={}, rows={}",
            productId,
            movements.size());

    int inserted = ledger.insert(movements);
    int duplicates = movements.size() - inserted;

    log.info(
            "Statement processed: productId={}, read={}, inserted={}, duplicates={}",
            productId,
            movements.size(),
            inserted,
            duplicates);

    return new Result(
            movements.size(),
            inserted,
            duplicates);
  }

  private void validateAndSaveProduct(
          String productId,
          Product incoming) {

    validateProductId(productId, incoming);

    ledger.product(productId)
            .ifPresent(existing ->
                    validateProductUpdate(existing, incoming));

    ledger.saveProduct(incoming);

    log.debug(
            "Product snapshot processed: id={}, provider={}, type={}",
            incoming.id(),
            incoming.provider(),
            incoming.type());
  }

  private void validateProductId(
          String productId,
          Product incoming) {

    if (!productId.equals(incoming.id())) {
      throw new IllegalArgumentException(
              "Id de producto inconsistente: path=%s, snapshot=%s"
                      .formatted(productId, incoming.id()));
    }
  }

  private void validateProductUpdate(
          Product existing,
          Product incoming) {

    validateProductIdentity(existing, incoming);
    validateBalance(existing, incoming);
  }

  private void validateProductIdentity(
          Product existing,
          Product incoming) {

    boolean identityChanged =
            existing.provider() != incoming.provider()
                    || existing.type() != incoming.type()
                    || !Objects.equals(
                    existing.currency(),
                    incoming.currency())
                    || !Objects.equals(
                    existing.linkedAccountId(),
                    incoming.linkedAccountId());

    if (identityChanged) {
      throw new IllegalArgumentException(
              "La identidad o relación del producto requiere revisión administrativa");
    }
  }

  private void validateBalance(
          Product existing,
          Product incoming) {

    if (incoming.balanceAt().isBefore(existing.balanceAt())) {
      throw new IllegalArgumentException(
              "El saldo recibido es anterior al saldo registrado");
    }

    if (incoming.balanceAt().equals(existing.balanceAt())
            && incoming.balance().compareTo(existing.balance()) != 0) {
      throw new IllegalArgumentException(
              "Existe un saldo diferente para la misma fecha");
    }
  }

  private static final class DirectUnitOfWork implements UnitOfWorkPort {
    @Override
    public <T> T execute(Work<T> work) {
      return work.run();
    }
  }

  private void requireRegisteredProduct(String productId) {
    if (ledger.product(productId).isEmpty()) {
      throw new IllegalArgumentException(
              "Registra el producto antes de importar: " + productId);
    }
  }
}