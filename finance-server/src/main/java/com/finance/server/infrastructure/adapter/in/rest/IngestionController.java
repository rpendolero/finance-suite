package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.Product;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.ImportService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/importer")
@RequiredArgsConstructor
public class IngestionController {
  private final LedgerPort ledger;
  private final ImportService imports;
  private final ProductMapper mapper;

  @PostMapping(value = "/products/{id}/batches", consumes = "multipart/form-data")
  @Transactional
  public ImportService.Result ingest(
      @PathVariable String id,
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart(value = "product", required = false) ProductDto snapshot)
      throws IOException {
    if (!id.matches("[a-zA-Z0-9_-]{1,64}"))
      throw new IllegalArgumentException("Id de producto inválido");
    if (snapshot != null) {
      if (!id.equals(snapshot.getId()))
        throw new IllegalArgumentException("Id de producto inconsistente");
      Product incoming = mapper.toDomain(snapshot);
      var existing = ledger.product(id);
      existing.ifPresent(
          old -> {
            if (old.provider() != incoming.provider()
                || old.type() != incoming.type()
                || !old.currency().equals(incoming.currency())
                || !java.util.Objects.equals(old.linkedAccountId(), incoming.linkedAccountId()))
              throw new IllegalArgumentException(
                  "La identidad o relación del producto requiere revisión administrativa");
            if (incoming.balanceAt().isBefore(old.balanceAt())
                || (incoming.balanceAt().equals(old.balanceAt())
                    && incoming.balance().compareTo(old.balance()) != 0))
              throw new IllegalArgumentException(
                  "Saldo anterior o contradictorio: actualizar el snapshot o enviar solo"
                      + " movimientos");
          });
      ledger.saveProduct(incoming);
    }
    try (var input = file.getInputStream()) {
      return imports.importCsv(input, id);
    }
  }
}
