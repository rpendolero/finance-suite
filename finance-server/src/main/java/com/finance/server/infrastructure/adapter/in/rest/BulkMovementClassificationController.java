package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.server.application.service.ClassificationManagementService;
import com.finance.server.infrastructure.adapter.in.dto.BulkClassificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movements/classification")
@RequiredArgsConstructor
public class BulkMovementClassificationController {
  private final ClassificationManagementService classification;

  @PatchMapping
  public ClassificationManagementService.BulkResult classify(
      @Valid @RequestBody BulkClassificationRequest request) {
    return classification.classifySelection(
        request.movementIds(), request.category(), request.subcategory(), request.kind());
  }
}
