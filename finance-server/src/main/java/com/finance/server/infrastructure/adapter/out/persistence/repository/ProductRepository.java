package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import com.finance.domain.Product;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  List<ProductEntity> findAllByOrderByIdAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ProductEntity p where p.id = :id")
  Optional<ProductEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") String id);

  boolean existsByProviderAndExternalIdAndIdNot(Product.Provider provider, String externalId, String id);
}
