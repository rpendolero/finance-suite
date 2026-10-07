package com.finance.server.application.service;

import com.finance.server.application.port.CategoryCatalogPort;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** Provides and validates the canonical category catalog persisted in the database. */
@RequiredArgsConstructor
public final class CategoryCatalogService {

  public static final String UNCLASSIFIED = "UNCLASSIFIED";

  public record CategoryDefinition(String code, String label, List<String> subcategories) {}

  private final CategoryCatalogPort catalog;

  private static final Map<String, String> ALIASES = aliases();

  public List<CategoryDefinition> categories() {
    return catalog.findAllActive().stream()
        .map(category -> new CategoryDefinition(
            category.code(),
            category.name(),
            category.subcategories().stream().map(CategoryCatalogPort.Subcategory::code).toList()))
        .toList();
  }

  public String normalizeCategory(String raw) {
    String code = normalizeCode(raw);
    if (code.isBlank() || UNCLASSIFIED.equals(code)) return UNCLASSIFIED;
    String aliased = ALIASES.getOrDefault(code, code);
    return catalog.findActiveByCode(aliased).isPresent() ? aliased : UNCLASSIFIED;
  }

  public String normalizeSubcategory(String category, String raw) {
    if (raw == null || raw.isBlank()) return null;
    String canonical = ALIASES.getOrDefault(normalizeCode(category), normalizeCode(category));
    String code = normalizeCode(raw);
    return catalog.findActiveByCode(canonical)
        .filter(value -> value.subcategories().stream().anyMatch(subcategory -> subcategory.code().equals(code)))
        .map(ignored -> code)
        .orElse(null);
  }

  public void validate(String category, String subcategory) {
    String canonical = normalizeCategory(category);
    if (UNCLASSIFIED.equals(canonical)) {
      throw new IllegalArgumentException("Categoría no válida: " + category);
    }
    if (subcategory != null && !subcategory.isBlank()
        && normalizeSubcategory(canonical, subcategory) == null) {
      throw new IllegalArgumentException(
          "Subcategoría no válida para " + canonical + ": " + subcategory);
    }
  }

  private static Map<String, String> aliases() {
    Map<String, String> aliases = new HashMap<>();
    add(aliases, "INGRESOS", "INCOME", "SALARY", "NOMINA");
    add(aliases, "VIVIENDA", "HOUSING", "HOME", "UTILITIES");
    add(aliases, "ALIMENTACION", "FOOD", "GROCERIES", "SUPERMARKET", "RESTAURANTS");
    add(aliases, "TRANSPORTE", "TRANSPORT", "TRANSPORTATION", "FUEL");
    add(aliases, "OCIO", "LEISURE", "ENTERTAINMENT");
    add(aliases, "SALUD", "HEALTH", "MEDICAL");
    add(aliases, "EDUCACION", "EDUCATION");
    add(aliases, "SEGUROS", "INSURANCE");
    add(aliases, "COMPRAS", "SHOPPING", "RETAIL");
    add(aliases, "SUSCRIPCIONES", "SUBSCRIPTIONS", "SUBSCRIPTION");
    add(aliases, "IMPUESTOS", "TAX", "TAXES");
    add(aliases, "TRANSFERENCIAS", "TRANSFER", "TRANSFERS");
    add(aliases, "EFECTIVO", "CASH", "ATM");
    add(aliases, "OTROS", "OTHER", "OTHERS", "MISC");
    return Map.copyOf(aliases);
  }

  private static void add(Map<String, String> aliases, String canonical, String... values) {
    aliases.put(canonical, canonical);
    for (String value : values) aliases.put(value, canonical);
  }

  private static String normalizeCode(String value) {
    if (value == null) return "";
    String ascii =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toUpperCase(Locale.ROOT);
    return Arrays.stream(ascii.split("[^A-Z0-9]+"))
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining("_"));
  }
}
