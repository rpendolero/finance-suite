package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
  @EntityGraph(attributePaths = "subcategories")
  List<CategoryEntity> findByActiveTrueOrderByDisplayOrderAscIdAsc();

  @EntityGraph(attributePaths = "subcategories")
  Optional<CategoryEntity> findByCodeAndActiveTrue(String code);
}
