package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Period;
import com.finance.server.application.service.ClassificationManagementService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClassificationController {

  private final ClassificationManagementService classification;

  @GetMapping("/categories")
  public Object categories() {
    return classification.categories();
  }

  @GetMapping("/classification/unclassified")
  public Object unclassified(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String productId,
      @RequestParam(defaultValue = "100") int limit) {
    return classification.unclassified(
        new Period(LocalDate.parse(from), LocalDate.parse(to)), productId, limit);
  }

  @PostMapping("/classification/rules")
  public Object createRule(@RequestBody ClassificationRule rule) {
    return classification.saveRule(rule);
  }

  @PostMapping("/classification/reclassify")
  public Object reclassify() {
    return classification.reclassifyAll();
  }
}
