package com.finance.server.application.service;

import com.finance.server.application.port.CategoryCatalogAdminPort;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class CategoryAdministrationService {
  private final CategoryCatalogAdminPort catalog;

  public List<CategoryCatalogAdminPort.CategoryItem> categories() { return catalog.findAll(); }

  public CategoryCatalogAdminPort.CategoryItem createCategory(String code, String name, boolean active, int order) {
    return catalog.saveCategory(new CategoryCatalogAdminPort.CategoryCommand(normalizeCode(code), name.trim(), active, order));
  }

  public CategoryCatalogAdminPort.CategoryItem updateCategory(String code, String name, boolean active, int order) {
    return catalog.updateCategory(normalizeCode(code), new CategoryCatalogAdminPort.CategoryCommand(normalizeCode(code), name.trim(), active, order));
  }

  public CategoryCatalogAdminPort.SubcategoryItem createSubcategory(String category, String code, String name, boolean active, int order) {
    return catalog.saveSubcategory(normalizeCode(category), new CategoryCatalogAdminPort.SubcategoryCommand(normalizeCode(code), name.trim(), active, order));
  }

  public CategoryCatalogAdminPort.SubcategoryItem updateSubcategory(String category, String code, String name, boolean active, int order) {
    return catalog.updateSubcategory(normalizeCode(category), normalizeCode(code), new CategoryCatalogAdminPort.SubcategoryCommand(normalizeCode(code), name.trim(), active, order));
  }

  private String normalizeCode(String value) {
    String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    return Arrays.stream(ascii.split("[^A-Z0-9]+")).filter(s -> !s.isBlank()).collect(Collectors.joining("_"));
  }
}
