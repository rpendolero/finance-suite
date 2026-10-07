package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.SubcategoryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcategoryRepository extends JpaRepository<SubcategoryEntity, Long> {
  Optional<SubcategoryEntity> findByCategoryCodeAndCode(String categoryCode, String code);
}
