package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.*;
import com.finance.server.application.port.FinanceQueries;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinanceAnalysisController {
  private final FinanceQueries queries;
  private Period period(String from, String to) {
    return new Period(LocalDate.parse(from), LocalDate.parse(to));
  }

  @GetMapping("/analysis/providers/{provider}")
  public Object providerSummary(
      @PathVariable Product.Provider provider, @RequestParam String from, @RequestParam String to) {
    return queries.providerSummary(period(from, to), provider);
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
