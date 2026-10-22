package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.*;
import com.finance.server.application.port.*;
import com.finance.server.application.service.*;
import com.finance.server.infrastructure.adapter.in.dto.*;
import com.finance.server.infrastructure.adapter.in.mapper.ProductMapper;
import com.finance.statements.StatementFormat;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
  private final FinanceQueries queries;
  private final ImportService imports;
  private final LedgerPort ledger;
  private final ProductMapper mapper;

  @GetMapping("/products")
  public Object products() {
    return queries.products().stream().map(mapper::toDto).toList();
  }

  @PostMapping("/products")
  public Object createProduct(@Valid @RequestBody ProductDto dto) {
    if (dto.getId() != null && !dto.getId().isBlank())
      throw new IllegalArgumentException("El ID del nuevo producto lo genera el servidor");
    String id = UUID.randomUUID().toString();
    dto.setId(id);
    ledger.saveProduct(mapper.toDomain(dto));
    return mapper.toDto(ledger.product(id).orElseThrow());
  }

  @PutMapping("/products/{id}")
  public Object product(@PathVariable String id, @Valid @RequestBody ProductDto dto) {
    if (!id.equals(dto.getId()))
      throw new IllegalArgumentException("id URL y cuerpo deben coincidir");
    ledger.saveProduct(mapper.toDomain(dto));
    return mapper.toDto(ledger.product(id).orElseThrow());
  }

  @DeleteMapping("/products/{id}")
  public void deleteProduct(@PathVariable String id) {
    ledger.deleteProduct(id);
  }

  @GetMapping("/products/{id}/import-formats")
  public List<StatementFormatDto> importFormats(@PathVariable String id) {
    return imports.availableFormats(id).stream().map(StatementFormatDto::from).toList();
  }

  @PostMapping(value = "/products/{id}/imports", consumes = "multipart/form-data")
  public ImportService.Result importStatement(
      @PathVariable String id, @RequestPart MultipartFile file,
      @RequestParam(defaultValue = "CSV") String format) throws IOException {
    StatementFormat selected;
    try {
      selected = StatementFormat.valueOf(format);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("Formato de importación desconocido");
    }
    if (file.isEmpty()) throw new IllegalArgumentException("El fichero está vacío");
    if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Máximo 10 MB por fichero");
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(selected.extension()))
      throw new IllegalArgumentException("Selecciona un fichero " + selected.extension() + " para este formato");
    try (var input = file.getInputStream()) {
      return imports.importStatement(input, id, selected);
    }
  }

}
