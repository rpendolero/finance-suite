package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.*;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import com.finance.server.infrastructure.adapter.out.persistence.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JpaLedgerAdapter implements LedgerPort {
  private final ProductRepository products;
  private final MovementRepository movements;
  private final PersistenceMapper mapper;

  public List<Product> products() {
    return products.findAllByOrderByIdAsc().stream().map(mapper::toDomain).toList();
  }

  public Optional<Product> product(String id) {
    return products.findById(id).map(mapper::toDomain);
  }

  @Transactional
  public void saveProduct(Product product) {
    var existing = products.findByIdForUpdate(product.id()).orElse(null);
    var incoming = mapper.toEntity(product);
    if (existing != null) {
      if (existing.getProvider() != product.provider() || existing.getType() != product.type())
        throw new IllegalArgumentException("No reutilices el ID de un producto para otro banco o tipo");
      if (existing.getExternalId() != null && product.externalId() != null
          && !existing.getExternalId().equals(product.externalId()))
        throw new IllegalArgumentException("No reutilices el ID de un producto para otra referencia bancaria");
      // Older snapshots omit these optional fields; keep the registered identity.
      if (incoming.getExternalId() == null) incoming.setExternalId(existing.getExternalId());
      if (incoming.getMaskedPan() == null) incoming.setMaskedPan(existing.getMaskedPan());
    }
    if (incoming.getExternalId() != null && products.existsByProviderAndExternalIdAndIdNot(
        product.provider(), incoming.getExternalId(), product.id()))
      throw new IllegalArgumentException("La referencia bancaria ya está registrada con otro ID");
    if (product.linkedAccountId() != null) {
      var account = products.findById(product.linkedAccountId()).orElse(null);
      if (account == null || account.getType() != Product.ProductType.ACCOUNT
          || account.getProvider() != product.provider())
        throw new IllegalArgumentException("La cuenta vinculada debe existir y pertenecer al mismo banco");
    }
    products.saveAndFlush(incoming);
    log.debug("Product snapshot persisted using Spring Data JPA");
  }

  public List<Movement> movements(Period period, String productId) {
    var found = productId != null && !productId.isBlank()
        ? movements.findByProductIdAndBookingDateBetweenOrderByBookingDateAscIdAsc(productId, period.from(), period.to())
        : movements.findByBookingDateBetweenOrderByBookingDateAscIdAsc(period.from(), period.to());
    return found.stream().map(mapper::toDomain).toList();
  }

  @Transactional
  public int insert(List<Movement> movements) {
    lockProducts(movements);
    int inserted = 0;
    for (var movement : movements) {
      var existing = this.movements.findByProductIdAndExternalId(movement.productId(), movement.externalId());
      if (existing.isEmpty()) {
        this.movements.save(mapper.toEntity(movement));
        inserted++;
      } else {
        reconcile(existing.orElseThrow(), movement);
      }
    }
    this.movements.flush();
    log.debug(
        "JPA batch processed: read={}, inserted={}, duplicates={}",
        movements.size(),
        inserted,
        movements.size() - inserted);
    return inserted;
  }

  private void lockProducts(List<Movement> movements) {
    // Serialize imports per product; sort locks to avoid cross-product lock inversion.
    for (String id : movements.stream().map(Movement::productId).distinct().sorted().toList())
      if (products.findByIdForUpdate(id).isEmpty())
        throw new IllegalArgumentException("Producto inexistente");
  }

  private void reconcile(MovementEntity existing, Movement incoming) {
    if (!existing.getBookingDate().equals(incoming.date())
        || existing.getAmount().compareTo(incoming.amount()) != 0
        || !existing.getDescription().equals(incoming.description()))
      throw new IllegalArgumentException(
          "Identificador bancario reutilizado con datos diferentes: revisar importación");
    if (existing.getStatus() == Movement.Status.PENDING
        && incoming.status() == Movement.Status.BOOKED) existing.setStatus(Movement.Status.BOOKED);
  }

  @Transactional
  public void classify(String id, String category, Movement.Kind kind) {
    var movement = movements.findById(id).orElse(null);
    if (movement == null) throw new IllegalArgumentException("Movimiento inexistente");
    movement.setCategory(category);
    movement.setKind(kind);
    movements.save(movement);
  }

  @Transactional
  public void deleteProduct(String id) {
    var product = products.findByIdForUpdate(id).orElse(null);
    if (product == null) return;
    movements.deleteAllByProductId(id);
    products.delete(product);
    products.flush();
    log.debug("Product and associated movements deleted using Spring Data JPA");
  }
}
