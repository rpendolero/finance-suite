package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.*;
import com.finance.domain.Period;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.application.service.ClassificationManagementService;
import com.finance.server.application.service.ExtendedAnalysisService;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {
  private final SettingsPort settings;
  private final ExtendedAnalysisService analysis;
  private final ClassificationManagementService classification;

  @GetMapping("/rules")
  public Object rules() {
    return settings.rules();
  }

  @PutMapping("/rules/{id}")
  public void rule(@PathVariable String id, @RequestBody ClassificationRule rule) {
    if (!id.equals(rule.id())) throw new IllegalArgumentException("id inconsistente");
    classification.saveRule(rule);
  }

  @DeleteMapping("/rules/{id}")
  public void deleteRule(@PathVariable String id) {
    settings.deleteRule(id);
  }

  @PutMapping("/budgets")
  public void budget(@RequestBody Budget budget) {
    settings.saveBudget(budget);
  }

  @GetMapping("/analysis/budgets")
  public Object budgets(@RequestParam String month) {
    return analysis.budgets(month);
  }

  @GetMapping("/analysis/trend")
  public Object trend(@RequestParam String fromMonth, @RequestParam String toMonth) {
    return analysis.trend(fromMonth, toMonth);
  }

  @GetMapping("/analysis/merchants")
  public Object merchants(@RequestParam String from, @RequestParam String to) {
    return analysis.merchants(new Period(LocalDate.parse(from), LocalDate.parse(to)));
  }

  @GetMapping("/analysis/reconciliation")
  public Object reconciliation(@RequestParam String from, @RequestParam String to) {
    return analysis.reconciliation(new Period(LocalDate.parse(from), LocalDate.parse(to)));
  }

  @GetMapping("/analysis/recurring-increases")
  public Object increases(@RequestParam String from, @RequestParam String to) {
    return analysis.increases(new Period(LocalDate.parse(from), LocalDate.parse(to)));
  }

  @GetMapping("/analysis/cash-flow")
  public Object cashFlow(@RequestParam String from, @RequestParam String to) {
    return analysis.cashFlow(new Period(LocalDate.parse(from), LocalDate.parse(to)));
  }
}
