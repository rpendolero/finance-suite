package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.*;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import jakarta.persistence.*;
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
  private final EntityManager entities;
  private final PersistenceMapper mapper;

  public List<Product> products() {
    return entities
        .createQuery("select p from ProductEntity p order by p.id", ProductEntity.class)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  public Optional<Product> product(String id) {
    return Optional.ofNullable(entities.find(ProductEntity.class, id)).map(mapper::toDomain);
  }

  @Transactional
  public void saveProduct(Product product) {
    var existing = entities.find(ProductEntity.class, product.id(), LockModeType.PESSIMISTIC_WRITE);
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
    if (incoming.getExternalId() != null && !entities.createQuery(
        "select p.id from ProductEntity p where p.provider = :provider"
            + " and p.externalId = :externalId and p.id <> :id", String.class)
        .setParameter("provider", product.provider())
        .setParameter("externalId", incoming.getExternalId())
        .setParameter("id", product.id()).getResultList().isEmpty())
      throw new IllegalArgumentException("La referencia bancaria ya está registrada con otro ID");
    if (product.linkedAccountId() != null) {
      var account = entities.find(ProductEntity.class, product.linkedAccountId());
      if (account == null || account.getType() != Product.ProductType.ACCOUNT
          || account.getProvider() != product.provider())
        throw new IllegalArgumentException("La cuenta vinculada debe existir y pertenecer al mismo banco");
    }
    entities.merge(incoming);
    entities.flush();
    log.debug("Product snapshot persisted using JPA");
  }

  public List<Movement> movements(Period period, String productId) {
    boolean filtered = productId != null && !productId.isBlank();
    var query =
        entities
            .createQuery(
                "select m from MovementEntity m where m.bookingDate between :from and :to"
                    + (filtered ? " and m.productId = :productId" : "")
                    + " order by m.bookingDate, m.id",
                MovementEntity.class)
            .setParameter("from", period.from())
            .setParameter("to", period.to());
    if (filtered) query.setParameter("productId", productId);
    return query.getResultList().stream().map(mapper::toDomain).toList();
  }

  @Transactional
  public int insert(List<Movement> movements) {
    lockProducts(movements);
    int inserted = 0;
    for (var movement : movements) {
      var existing =
          entities
              .createQuery(
                  "select m from MovementEntity m where m.productId = :productId and m.externalId ="
                      + " :externalId",
                  MovementEntity.class)
              .setParameter("productId", movement.productId())
              .setParameter("externalId", movement.externalId())
              .getResultList();
      if (existing.isEmpty()) {
        entities.persist(mapper.toEntity(movement));
        inserted++;
      } else {
        reconcile(existing.getFirst(), movement);
      }
    }
    entities.flush();
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
      if (entities.find(ProductEntity.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
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
    var movement = entities.find(MovementEntity.class, id);
    if (movement == null) throw new IllegalArgumentException("Movimiento inexistente");
    movement.setCategory(category);
    movement.setKind(kind);
  }

  @Transactional
  public void deleteProduct(String id) {
    var product = entities.find(ProductEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (product == null) return;
    entities
        .createQuery("delete from MovementEntity m where m.productId = :id")
        .setParameter("id", id)
        .executeUpdate();
    entities.remove(product);
    entities.flush();
    log.debug("Product and associated movements deleted using JPA");
  }
}
