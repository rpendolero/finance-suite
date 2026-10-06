package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.Product;
import com.finance.server.application.service.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/importer")
@RequiredArgsConstructor
public class IngestionController {

    private static final Pattern PRODUCT_ID_PATTERN =
            Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private final ImportService imports;
    private final ProductMapper mapper;

    @PostMapping(
            value = "/products/{id}/batches",
            consumes = "multipart/form-data")
    public ImportService.Result ingest(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart(value = "product", required = false)
            ProductDto snapshot)
            throws IOException {

        validateProductId(id);
        validateFile(file);

        Product product =
                snapshot != null
                        ? mapper.toDomain(snapshot)
                        : null;

        try (var input = file.getInputStream()) {
            return imports.importBatch(input, id, product);
        }
    }

    private void validateProductId(String id) {
        if (!PRODUCT_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Id de producto inválido: " + id);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "El fichero de movimientos está vacío");
        }
    }
}