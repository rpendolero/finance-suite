package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.LocalDate;

public interface MovementRepository extends JpaRepository<MovementEntity, String> {
  List<MovementEntity> findByBookingDateBetweenOrderByBookingDateAscIdAsc(LocalDate from, LocalDate to);
  List<MovementEntity> findByProductIdAndBookingDateBetweenOrderByBookingDateAscIdAsc(String productId, LocalDate from, LocalDate to);
  Optional<MovementEntity> findByProductIdAndExternalId(String productId, String externalId);

  @Modifying(flushAutomatically = true)
  @Query("delete from MovementEntity m where m.productId = :productId")
  int deleteAllByProductId(@org.springframework.data.repository.query.Param("productId") String productId);
}
