package com.finance.server.infrastructure.adapter.out.persistence.adapter;

import com.finance.server.application.port.CategoryCatalogAdminPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import com.finance.server.infrastructure.adapter.out.persistence.entity.SubcategoryEntity;
import com.finance.server.infrastructure.adapter.out.persistence.repository.CategoryRepository;
import com.finance.server.infrastructure.adapter.out.persistence.repository.SubcategoryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class JpaCategoryCatalogAdminAdapter implements CategoryCatalogAdminPort {
  private final CategoryRepository categories;
  private final SubcategoryRepository subcategories;

  @Override @Transactional(readOnly = true)
  public List<CategoryItem> findAll() {
    return categories.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::toItem).toList();
  }

  @Override @Transactional(readOnly = true)
  public Optional<CategoryItem> findByCode(String code) {
    return categories.findByCode(code).map(this::toItem);
  }

  @Override
  public CategoryItem saveCategory(CategoryCommand command) {
    if (categories.findByCode(command.code()).isPresent()) throw new IllegalArgumentException("Category already exists: " + command.code());
    CategoryEntity entity = new CategoryEntity();
    entity.setCode(command.code());
    setCategoryValues(entity, command);
    return toItem(categories.save(entity));
  }

  @Override
  public CategoryItem updateCategory(String code, CategoryCommand command) {
    CategoryEntity entity = categories.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Category not found: " + code));
    setCategoryValues(entity, command);
    return toItem(categories.save(entity));
  }

  @Override
  public SubcategoryItem saveSubcategory(String categoryCode, SubcategoryCommand command) {
    CategoryEntity category = categories.findByCode(categoryCode).orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryCode));
    if (subcategories.findByCategoryCodeAndCode(categoryCode, command.code()).isPresent()) throw new IllegalArgumentException("Subcategory already exists: " + command.code());
    SubcategoryEntity entity = new SubcategoryEntity();
    entity.setCategory(category);
    entity.setCode(command.code());
    setSubcategoryValues(entity, command);
    return toItem(subcategories.save(entity));
  }

  @Override
  public SubcategoryItem updateSubcategory(String categoryCode, String code, SubcategoryCommand command) {
    SubcategoryEntity entity = subcategories.findByCategoryCodeAndCode(categoryCode, code).orElseThrow(() -> new IllegalArgumentException("Subcategory not found: " + code));
    setSubcategoryValues(entity, command);
    return toItem(subcategories.save(entity));
  }

  private void setCategoryValues(CategoryEntity entity, CategoryCommand command) {
    entity.setName(command.name()); entity.setActive(command.active()); entity.setDisplayOrder(command.displayOrder());
  }

  private void setSubcategoryValues(SubcategoryEntity entity, SubcategoryCommand command) {
    entity.setName(command.name()); entity.setActive(command.active()); entity.setDisplayOrder(command.displayOrder());
  }

  private CategoryItem toItem(CategoryEntity entity) {
    return new CategoryItem(entity.getId(), entity.getCode(), entity.getName(), entity.isActive(), entity.getDisplayOrder(), entity.getSubcategories().stream().map(this::toItem).toList());
  }

  private SubcategoryItem toItem(SubcategoryEntity entity) {
    return new SubcategoryItem(entity.getId(), entity.getCode(), entity.getName(), entity.isActive(), entity.getDisplayOrder());
  }
}
