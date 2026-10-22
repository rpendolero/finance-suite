package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.server.application.port.MovementSearchPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.MovementEntity;
import com.finance.server.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.finance.server.infrastructure.adapter.out.persistence.repository.MovementRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JpaMovementSearchAdapter implements MovementSearchPort {
  private final MovementRepository movementRepository;
  private final PersistenceMapper mapper;

  @Override
  public MovementSearchPort.Page search(MovementSearchPort.Criteria criteria) {
    int limit = Math.max(1, Math.min(criteria.limit(), 200));
    int offset = Math.max(0, criteria.offset());
    int page = offset / limit;

    Specification<MovementEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(
              cb.between(
                  root.get("bookingDate"), criteria.period().from(), criteria.period().to()));
          if (hasText(criteria.productId()))
            predicates.add(cb.equal(root.get("productId"), criteria.productId()));
          if (hasText(criteria.category()))
            predicates.add(
                cb.equal(
                    cb.lower(root.get("category")), criteria.category().toLowerCase()));
          if (hasText(criteria.merchant()))
            predicates.add(
                cb.like(
                    cb.lower(root.get("merchant")),
                    "%" + criteria.merchant().toLowerCase() + "%"));
          if (hasText(criteria.text())) {
            String pattern = "%" + criteria.text().toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("merchant")), pattern)));
          }
          if (criteria.minAmount() != null)
            predicates.add(
                cb.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
          if (criteria.maxAmount() != null)
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
          if (criteria.kind() != null) predicates.add(cb.equal(root.get("kind"), criteria.kind()));
          if (criteria.status() != null)
            predicates.add(cb.equal(root.get("status"), criteria.status()));
          return cb.and(predicates.toArray(Predicate[]::new));
        };

    var result =
        movementRepository.findAll(spec, PageRequest.of(page, limit, movementSort(criteria)));
    var items = result.getContent().stream().map(mapper::toDomain).toList();
    log.debug(
        "Movement search completed: offset={}, limit={}, returned={}, total={}",
        offset,
        limit,
        items.size(),
        result.getTotalElements());
    return new MovementSearchPort.Page(items, result.getTotalElements(), offset, limit);
  }

  private Sort movementSort(MovementSearchPort.Criteria criteria) {
    String property =
        switch (criteria.sortBy() == null
            ? MovementSearchPort.Criteria.SortField.DATE
            : criteria.sortBy()) {
          case DATE -> "bookingDate";
          case AMOUNT -> "amount";
          case MERCHANT -> "merchant";
          case CATEGORY -> "category";
        };
    Sort.Direction direction =
        criteria.sortDirection() == MovementSearchPort.Criteria.SortDirection.ASC
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property).and(Sort.by(direction, "id"));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
