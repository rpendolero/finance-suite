package com.finance.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finance.server.application.port.CategoryCatalogPort;
import com.finance.server.application.service.CategoryCatalogService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RestaurantCategoryCatalogTest {

  @Test
  void resolvesRestaurantAliasesAndValidatesSubcategoriesAgainstNewParent() {
    CategoryCatalogPort catalog = mock(CategoryCatalogPort.class);
    Map<String, CategoryCatalogPort.Category> definitions = Map.of(
        "ALIMENTACION", new CategoryCatalogPort.Category(
            "ALIMENTACION", "Alimentación",
            List.of(new CategoryCatalogPort.Subcategory("SUPERMERCADO", "Supermercado"),
                new CategoryCatalogPort.Subcategory("OTROS_ALIMENTOS", "Otros alimentos"))),
        "RESTAURACION", new CategoryCatalogPort.Category(
            "RESTAURACION", "Restauración",
            List.of(new CategoryCatalogPort.Subcategory("RESTAURANTES", "Restaurantes"),
                new CategoryCatalogPort.Subcategory("BARES_CAFETERIAS", "Bares y cafeterías"),
                new CategoryCatalogPort.Subcategory("COMIDA_DOMICILIO", "Comida a domicilio"))));
    when(catalog.findActiveByCode(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(definitions.get(invocation.getArgument(0))));
    when(catalog.findAllActive()).thenReturn(List.copyOf(definitions.values()));

    CategoryCatalogService service = new CategoryCatalogService(catalog);

    assertThat(service.normalizeCategory("RESTAURANTS")).isEqualTo("RESTAURACION");
    assertThat(service.normalizeCategory("Restaurantes")).isEqualTo("RESTAURACION");
    assertThat(service.normalizeCategory("restaurante")).isEqualTo("RESTAURACION");
    assertThat(service.normalizeCategory("Dining")).isEqualTo("RESTAURACION");
    assertThat(service.normalizeCategory("groceries")).isEqualTo("ALIMENTACION");
    assertThat(service.normalizeSubcategory("RESTAURACION", "BARES_CAFETERIAS"))
        .isEqualTo("BARES_CAFETERIAS");
    assertThat(service.normalizeSubcategory("ALIMENTACION", "RESTAURANTES")).isNull();
    assertThat(service.normalizeSubcategory("RESTAURACION", "SUPERMERCADO")).isNull();
    service.validate("RESTAURACION", "COMIDA_DOMICILIO");
    assertThatThrownBy(() -> service.validate("ALIMENTACION", "RESTAURANTES"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.categories()).extracting(CategoryCatalogService.CategoryDefinition::code)
        .contains("RESTAURACION", "ALIMENTACION");
  }
}
