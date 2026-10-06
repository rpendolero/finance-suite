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
    entities.find(ProductEntity.class, product.id(), LockModeType.PESSIMISTIC_WRITE);
    entities.merge(mapper.toEntity(product));
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
