package com.finance.server.application.port;

import java.util.List;
import java.util.Optional;

public interface CategoryCatalogPort {
  record Category(String code, String name, List<Subcategory> subcategories) {}
  record Subcategory(String code, String name) {}

  List<Category> findAllActive();
  Optional<Category> findActiveByCode(String code);
}
