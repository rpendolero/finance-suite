package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.*;
import com.finance.server.application.port.FinanceQueries;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.MovementSearchPort;
import java.math.BigDecimal;
import com.finance.server.application.service.*;
import com.finance.server.infrastructure.adapter.in.dto.ProductDto;
import com.finance.server.infrastructure.adapter.in.mapper.ProductMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinanceController {
  private final FinanceQueries queries;

  @GetMapping("/analysis/providers/{provider}")
  public Object providerSummary(
      @PathVariable Product.Provider provider, @RequestParam String from, @RequestParam String to) {
    return queries.providerSummary(period(from, to), provider);
  }

  private final ImportService imports;
  private final LedgerPort ledger;
  private final MovementSearchPort movementSearch;
  private final MovementExportService movementExport;
  private final ClassificationManagementService classification;
  private final ProductMapper mapper;

  private Period period(String from, String to) {
    return new Period(LocalDate.parse(from), LocalDate.parse(to));
  }

  @GetMapping("/products")
  public Object products() {
    return queries.products().stream().map(mapper::toDto).toList();
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

  @PostMapping(value = "/products/{id}/imports", consumes = "multipart/form-data")
  public Object importCsv(@PathVariable String id, @RequestPart MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      return imports.importCsv(input, id);
    }
  }

  @GetMapping("/movements")
  public Object movements(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String productId,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit) {
    return queries.movements(period(from, to), productId, offset, limit);
  }


  @GetMapping("/movements/search")
  public MovementSearchPort.Page searchMovements(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String merchant,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) Movement.Kind kind,
      @RequestParam(required = false) Movement.Status status,
      @RequestParam(defaultValue = "DATE") MovementSearchPort.Criteria.SortField sortBy,
      @RequestParam(defaultValue = "DESC") MovementSearchPort.Criteria.SortDirection sortDirection,
      @RequestParam(defaultValue = "0") @Min(0) int offset,
      @RequestParam(defaultValue = "25") @Min(1) @Max(200) int limit) {
    return movementSearch.search(new MovementSearchPort.Criteria(
        period(from, to), productId, category, merchant, text, minAmount, maxAmount, kind, status, sortBy, sortDirection, offset, limit));
  }

  @GetMapping(value = "/movements/export", produces = "text/csv")
  public ResponseEntity<byte[]> exportMovements(
      @RequestParam String from, @RequestParam String to,
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String merchant,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) Movement.Kind kind,
      @RequestParam(required = false) Movement.Status status,
      @RequestParam(defaultValue = "DATE") MovementSearchPort.Criteria.SortField sortBy,
      @RequestParam(defaultValue = "DESC") MovementSearchPort.Criteria.SortDirection sortDirection) {
    var criteria = new MovementSearchPort.Criteria(period(from, to), productId, category, merchant, text,
        minAmount, maxAmount, kind, status, sortBy, sortDirection, 0, 200);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=movimientos-" + from + "-" + to + ".csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(movementExport.exportCsv(criteria));
  }

  public record Classification(
      @NotBlank @Size(max = 64) String category,
      @Size(max = 64) String subcategory,
      @NotNull Movement.Kind kind,
      boolean createRule,
      boolean applyToSimilar) {}

  @PatchMapping("/movements/{id}/classification")
  public Object classify(@PathVariable String id, @Valid @RequestBody Classification c) {
    return classification.classifyManually(
        id,
        c.category(),
        c.subcategory(),
        c.kind(),
        c.createRule(),
        c.applyToSimilar());
  }

  @GetMapping("/analysis/summary")
  public Object summary(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String productId) {
    return queries.summary(period(from, to), productId);
  }

  @GetMapping("/analysis/compare")
  public Object compare(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam String previousFrom,
      @RequestParam String previousTo,
      @RequestParam(required = false) String productId) {
    return queries.compare(period(from, to), period(previousFrom, previousTo), productId);
  }

  @GetMapping("/analysis/recurring")
  public Object recurring(@RequestParam String from, @RequestParam String to) {
    return queries.recurring(period(from, to));
  }

  @GetMapping("/analysis/anomalies")
  public Object anomalies(@RequestParam String from, @RequestParam String to) {
    return queries.anomalies(period(from, to));
  }

  @GetMapping("/analysis/cards")
  public Object cards() {
    return queries.cards();
  }

  @GetMapping("/analysis/forecast")
  public Object forecast(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(defaultValue = "30") int days) {
    return queries.forecast(period(from, to), days);
  }

  @GetMapping("/analysis/quality")
  public Object quality(@RequestParam String from, @RequestParam String to) {
    return queries.quality(period(from, to));
  }
}
