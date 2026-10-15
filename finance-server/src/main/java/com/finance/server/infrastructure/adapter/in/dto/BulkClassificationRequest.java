package com.finance.server.infrastructure.adapter.in.dto;

import com.finance.domain.Movement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkClassificationRequest(
    @NotEmpty List<@NotBlank String> movementIds,
    @NotBlank @Size(max = 64) String category,
    @Size(max = 64) String subcategory,
    @NotNull Movement.Kind kind) {}
