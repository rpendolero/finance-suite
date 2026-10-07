package com.finance.server.application.service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Defines the canonical categories exposed to the dashboard and classification use cases. */
public final class CategoryCatalogService {

  public static final String UNCLASSIFIED = "UNCLASSIFIED";

  public record CategoryDefinition(String code, String label, List<String> subcategories) {}

  private static final List<CategoryDefinition> CATEGORIES =
      List.of(
          category("INGRESOS", "Ingresos", "NOMINA", "DEVOLUCIONES", "OTROS_INGRESOS"),
          category("VIVIENDA", "Vivienda", "HIPOTECA_ALQUILER", "ELECTRICIDAD", "GAS", "AGUA", "INTERNET", "COMUNIDAD"),
          category("ALIMENTACION", "Alimentación", "SUPERMERCADO", "RESTAURANTES", "COMIDA_DOMICILIO"),
          category("TRANSPORTE", "Transporte", "COMBUSTIBLE", "TRANSPORTE_PUBLICO", "APARCAMIENTO", "PEAJES", "MANTENIMIENTO_COCHE"),
          category("OCIO", "Ocio", "VIAJES", "CINE_TEATRO", "DEPORTE", "OTROS_OCIO"),
          category("SALUD", "Salud", "FARMACIA", "MEDICO", "DENTISTA", "OTROS_SALUD"),
          category("EDUCACION", "Educación", "COLEGIO_UNIVERSIDAD", "CURSOS", "LIBROS_MATERIAL"),
          category("SEGUROS", "Seguros", "HOGAR", "AUTO", "SALUD", "VIDA", "OTROS_SEGUROS"),
          category("COMPRAS", "Compras", "ONLINE", "ROPA", "HOGAR", "TECNOLOGIA", "OTRAS_COMPRAS"),
          category("SUSCRIPCIONES", "Suscripciones", "STREAMING", "SOFTWARE", "TELEFONIA", "OTRAS_SUSCRIPCIONES"),
          category("IMPUESTOS", "Impuestos", "HACIENDA", "TASAS", "MULTAS"),
          category("TRANSFERENCIAS", "Transferencias", "TRASPASO_INTERNO", "LIQUIDACION_TARJETA", "TRANSFERENCIA_EXTERNA"),
          category("EFECTIVO", "Efectivo", "RETIRADA_CAJERO"),
          category("OTROS", "Otros", "OTROS"));

  private static final Map<String, String> ALIASES = aliases();

  public List<CategoryDefinition> categories() {
    return CATEGORIES;
  }

  public String normalizeCategory(String raw) {
    String code = normalizeCode(raw);
    if (code.isBlank() || UNCLASSIFIED.equals(code)) return UNCLASSIFIED;
    String aliased = ALIASES.getOrDefault(code, code);
    return isCategory(aliased) ? aliased : UNCLASSIFIED;
  }

  public String normalizeSubcategory(String category, String raw) {
    if (raw == null || raw.isBlank()) return null;
    String code = normalizeCode(raw);
    return definition(category)
        .filter(d -> d.subcategories().contains(code))
        .map(ignored -> code)
        .orElse(null);
  }

  public void validate(String category, String subcategory) {
    String canonical = normalizeCategory(category);
    if (UNCLASSIFIED.equals(canonical)) {
      throw new IllegalArgumentException("Categoría no válida: " + category);
    }
    if (subcategory != null && !subcategory.isBlank() && normalizeSubcategory(canonical, subcategory) == null) {
      throw new IllegalArgumentException(
          "Subcategoría no válida para " + canonical + ": " + subcategory);
    }
  }

  private boolean isCategory(String code) {
    return CATEGORIES.stream().anyMatch(c -> c.code().equals(code));
  }

  private Optional<CategoryDefinition> definition(String category) {
    String canonical = ALIASES.getOrDefault(normalizeCode(category), normalizeCode(category));
    return CATEGORIES.stream().filter(c -> c.code().equals(canonical)).findFirst();
  }

  private static CategoryDefinition category(String code, String label, String... subcategories) {
    return new CategoryDefinition(code, label, List.of(subcategories));
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
