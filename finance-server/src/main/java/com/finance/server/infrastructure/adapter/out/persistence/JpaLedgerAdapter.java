package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.Movement;
import com.finance.domain.Period;
import com.finance.domain.Product;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.MovementEntity;
import com.finance.server.infrastructure.adapter.out.persistence.entity.ProductEntity;
import com.finance.server.infrastructure.adapter.out.persistence.repository.MovementRepository;
import com.finance.server.infrastructure.adapter.out.persistence.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JpaLedgerAdapter implements LedgerPort {

  private static final String PRODUCT_NOT_FOUND = "Producto inexistente";

  private final ProductRepository productRepository;
  private final MovementRepository movementRepository;
  private final PersistenceMapper mapper;

  @Override
  public List<Product> products() {
    return productRepository.findAllByOrderByIdAsc()
            .stream()
            .map(mapper::toDomain)
            .toList();
  }

  @Override
  public Optional<Product> product(String id) {
    return productRepository.findById(id)
            .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public void saveProduct(Product product) {
    ProductEntity incoming = mapper.toEntity(product);

    productRepository.findByIdForUpdate(product.id())
            .ifPresent(existing -> mergeAndValidate(existing, incoming, product));

    validateExternalIdUniqueness(product, incoming);
    validateLinkedAccount(product);

    productRepository.saveAndFlush(incoming);

    log.debug(
            "Product persisted: id={}, provider={}, type={}",
            product.id(),
            product.provider(),
            product.type());
  }

  @Override
  public List<Movement> movements(Period period, String productId) {
    List<MovementEntity> entities =
            hasText(productId)
                    ? movementRepository
                    .findByProductIdAndBookingDateBetweenOrderByBookingDateAscIdAsc(
                            productId, period.from(), period.to())
                    : movementRepository
                    .findByBookingDateBetweenOrderByBookingDateAscIdAsc(
                            period.from(), period.to());

    return entities.stream()
            .map(mapper::toDomain)
            .toList();
  }

  @Override
  @Transactional
  public int insert(List<Movement> movements) {
    if (movements.isEmpty()) {
      return 0;
    }

    lockProducts(movements);

    int inserted = 0;

    for (Movement movement : movements) {
      if (insertOrReconcile(movement)) {
        inserted++;
      }
    }

    movementRepository.flush();

    log.debug(
            "JPA batch processed: read={}, inserted={}, duplicates={}",
            movements.size(),
            inserted,
            movements.size() - inserted);

    return inserted;
  }

  @Override
  @Transactional
  public void classify(String id, String category, Movement.Kind kind) {
    MovementEntity movement = movementRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Movimiento inexistente: " + id));

    movement.setCategory(category);
    movement.setKind(kind);

    // No save() required: entity is managed by JPA.
  }

  @Override
  @Transactional
  public void deleteProduct(String id) {
    productRepository.findByIdForUpdate(id)
            .ifPresent(product -> {
              movementRepository.deleteAllByProductId(id);
              productRepository.delete(product);
              productRepository.flush();

              log.debug("Product and associated movements deleted: id={}", id);
            });
  }

  private boolean insertOrReconcile(Movement movement) {
    return movementRepository
            .findByProductIdAndExternalId(
                    movement.productId(),
                    movement.externalId())
            .map(existing -> {
              reconcile(existing, movement);
              return false;
            })
            .orElseGet(() -> {
              movementRepository.save(mapper.toEntity(movement));
              return true;
            });
  }

  private void lockProducts(List<Movement> movements) {
    movements.stream()
            .map(Movement::productId)
            .distinct()
            .sorted()
            .forEach(this::lockProduct);
  }

  private void lockProduct(String productId) {
    productRepository.findByIdForUpdate(productId)
            .orElseThrow(
                    () -> new IllegalArgumentException(
                            PRODUCT_NOT_FOUND + ": " + productId));
  }

  private void mergeAndValidate(
          ProductEntity existing,
          ProductEntity incoming,
          Product product) {

    validateProductIdentity(existing, product);

    // Old snapshots may omit optional identity fields.
    if (incoming.getExternalId() == null) {
      incoming.setExternalId(existing.getExternalId());
    }

    if (incoming.getMaskedPan() == null) {
      incoming.setMaskedPan(existing.getMaskedPan());
    }
  }

  private void validateProductIdentity(
          ProductEntity existing,
          Product product) {

    if (existing.getProvider() != product.provider()
            || existing.getType() != product.type()) {

      throw new IllegalArgumentException(
              "No reutilices el ID de un producto para otro banco o tipo");
    }

    if (existing.getExternalId() != null
            && product.externalId() != null
            && !existing.getExternalId().equals(product.externalId())) {

      throw new IllegalArgumentException(
              "No reutilices el ID de un producto para otra referencia bancaria");
    }
  }

  private void validateExternalIdUniqueness(
          Product product,
          ProductEntity incoming) {

    if (incoming.getExternalId() == null) {
      return;
    }

    boolean duplicated =
            productRepository.existsByProviderAndExternalIdAndIdNot(
                    product.provider(),
                    incoming.getExternalId(),
                    product.id());

    if (duplicated) {
      throw new IllegalArgumentException(
              "La referencia bancaria ya está registrada con otro ID");
    }
  }

  private void validateLinkedAccount(Product product) {
    if (product.linkedAccountId() == null) {
      return;
    }

    ProductEntity account =
            productRepository.findById(product.linkedAccountId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "La cuenta vinculada no existe: "
                                            + product.linkedAccountId()));

    if (account.getType() != Product.ProductType.ACCOUNT) {
      throw new IllegalArgumentException(
              "El producto vinculado no es una cuenta");
    }

    if (account.getProvider() != product.provider()) {
      throw new IllegalArgumentException(
              "La cuenta vinculada pertenece a otro banco");
    }
  }

  private void reconcile(
          MovementEntity existing,
          Movement incoming) {

    if (hasMovementChanged(existing, incoming)) {
      throw new IllegalArgumentException(
              "Identificador bancario reutilizado con datos diferentes: "
                      + incoming.externalId());
    }

    if (existing.getStatus() == Movement.Status.PENDING
            && incoming.status() == Movement.Status.BOOKED) {

      existing.setStatus(Movement.Status.BOOKED);
      // Dirty checking persists the change.
    }
  }

  private boolean hasMovementChanged(
          MovementEntity existing,
          Movement incoming) {

    return !existing.getBookingDate().equals(incoming.date())
            || existing.getAmount().compareTo(incoming.amount()) != 0
            || !existing.getDescription().equals(incoming.description());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}