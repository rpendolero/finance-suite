package com.finance.server.application.port;

import java.util.List;
import java.util.Optional;

public interface CategoryCatalogAdminPort {
  record CategoryItem(Long id, String code, String name, boolean active, int displayOrder, List<SubcategoryItem> subcategories) {}
  record SubcategoryItem(Long id, String code, String name, boolean active, int displayOrder) {}
  record CategoryCommand(String code, String name, boolean active, int displayOrder) {}
  record SubcategoryCommand(String code, String name, boolean active, int displayOrder) {}

  List<CategoryItem> findAll();
  Optional<CategoryItem> findByCode(String code);
  CategoryItem saveCategory(CategoryCommand command);
  CategoryItem updateCategory(String code, CategoryCommand command);
  SubcategoryItem saveSubcategory(String categoryCode, SubcategoryCommand command);
  SubcategoryItem updateSubcategory(String categoryCode, String code, SubcategoryCommand command);
}
