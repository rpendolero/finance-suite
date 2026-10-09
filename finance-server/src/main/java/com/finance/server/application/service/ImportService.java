package com.finance.server.application.service;

import com.finance.domain.Product;
import com.finance.statements.StatementFormat;
import java.util.Arrays;
import java.util.List;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.StatementParserPort;
import com.finance.server.application.port.UnitOfWorkPort;
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

    return importStatement(input, productId, StatementFormat.CSV);
  }

  public List<StatementFormat> availableFormats(String productId) {
    Product product = requireRegisteredProduct(productId);
    return Arrays.stream(StatementFormat.values()).filter(format -> format.supports(product)).toList();
  }

  public Result importStatement(InputStream input, String productId, StatementFormat format) {
    Product product = requireRegisteredProduct(productId);
    if (format == null || !format.supports(product))
      throw new IllegalArgumentException("El formato no corresponde a la entidad o tipo del producto");
    return unitOfWork.execute(() -> process(input, productId, format));
  }

  private Result process(InputStream input, String productId, StatementFormat format) {
    log.info("Statement processing started: productId={}, format={}", productId, format);
    var rules = settings.rules();
    var parsed = format == StatementFormat.CSV
        ? parser.parse(input, productId) : parser.parse(input, productId, format);
    var movements = parsed.stream().map(movement -> classification.classify(movement, rules)).toList();
    int inserted = ledger.insert(movements);
    int duplicates = movements.size() - inserted;
    log.info("Statement processed: productId={}, format={}, read={}, inserted={}, duplicates={}",
        productId, format, movements.size(), inserted, duplicates);
    return new Result(movements.size(), inserted, duplicates);
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

  private Product requireRegisteredProduct(String productId) {
    return ledger.product(productId).orElseThrow(() ->
        new IllegalArgumentException("Registra el producto antes de importar: " + productId));
  }
}
