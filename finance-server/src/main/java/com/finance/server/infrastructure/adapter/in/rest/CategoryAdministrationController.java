package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.server.application.service.CategoryAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class CategoryAdministrationController {
  private final CategoryAdministrationService service;

  public record CategoryRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=100) String name, boolean active, @Min(0) int displayOrder) {}
  public record SubcategoryRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=100) String name, boolean active, @Min(0) int displayOrder) {}

  @GetMapping public Object categories() { return service.categories(); }

  @PostMapping public Object create(@Valid @RequestBody CategoryRequest r) {
    return service.createCategory(r.code(), r.name(), r.active(), r.displayOrder());
  }

  @PutMapping("/{code}") public Object update(@PathVariable String code, @Valid @RequestBody CategoryRequest r) {
    return service.updateCategory(code, r.name(), r.active(), r.displayOrder());
  }

  @PostMapping("/{category}/subcategories") public Object createSubcategory(@PathVariable String category, @Valid @RequestBody SubcategoryRequest r) {
    return service.createSubcategory(category, r.code(), r.name(), r.active(), r.displayOrder());
  }

  @PutMapping("/{category}/subcategories/{code}") public Object updateSubcategory(@PathVariable String category, @PathVariable String code, @Valid @RequestBody SubcategoryRequest r) {
    return service.updateSubcategory(category, code, r.name(), r.active(), r.displayOrder());
  }
}
