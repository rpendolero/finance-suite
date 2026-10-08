package com.finance.server.infrastructure.adapter.out.persistence.adapter;

import com.finance.server.application.port.CategoryCatalogPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import com.finance.server.infrastructure.adapter.out.persistence.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaCategoryCatalogAdapter implements CategoryCatalogPort {
  private final CategoryRepository repository;

  @Override
  public List<Category> findAllActive() {
    return repository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<Category> findActiveByCode(String code) {
    return repository.findByCodeAndActiveTrue(code).map(this::toDomain);
  }

  private Category toDomain(CategoryEntity entity) {
    return new Category(
        entity.getCode(),
        entity.getName(),
        entity.getSubcategories().stream()
            .filter(subcategory -> subcategory.isActive())
            .map(subcategory -> new Subcategory(subcategory.getCode(), subcategory.getName()))
            .toList());
  }
}
