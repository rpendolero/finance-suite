package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.Period;
import com.finance.server.application.service.DashboardAnalysisService;
import com.finance.server.application.service.DashboardAnalysisService.FinancialFlow;
import com.finance.server.application.service.DashboardAnalysisService.FlowDirection;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  private final DashboardAnalysisService dashboard;

  private Period period(String from,String to){return new Period(LocalDate.parse(from),LocalDate.parse(to));}

  @GetMapping("/flows")
  public FinancialFlow flow(@RequestParam String from, @RequestParam String to,
      @RequestParam(required = false) String productId,
      @RequestParam(defaultValue = "EXPENSE") String direction,
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "25") int limit) {
    return dashboard.flow(period(from, to), productId, FlowDirection.valueOf(direction), offset, limit);
  }

  @GetMapping("/overview")
  public Object overview(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId){
    return dashboard.overview(period(from,to),productId);
  }

  @GetMapping("/trend")
  public Object trend(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId,
      @RequestParam(defaultValue="DAY") String groupBy){
    return dashboard.trend(period(from,to),productId,groupBy);
  }

  @GetMapping("/categories")
  public Object categories(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId){
    return dashboard.categories(period(from,to),productId);
  }

  @GetMapping("/merchants")
  public Object merchants(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId,
      @RequestParam(defaultValue="10") int limit){
    return dashboard.merchants(period(from,to),productId,limit);
  }

  @GetMapping("/products")
  public Object products(@RequestParam String from,@RequestParam String to){
    return dashboard.products(period(from,to));
  }

  @GetMapping("/calendar")
  public Object calendar(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId){
    return dashboard.calendar(period(from,to),productId);
  }

  @GetMapping("/insights")
  public Object insights(@RequestParam String from,@RequestParam String to,@RequestParam(required=false) String productId){
    return dashboard.insights(period(from,to),productId);
  }
}
