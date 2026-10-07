package com.finance.server.application.service;

import com.finance.domain.*;
import com.finance.server.application.port.LedgerPort;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DashboardAnalysisService {
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
  private final LedgerPort ledger;

  public record TrendPoint(LocalDate date, BigDecimal income, BigDecimal expenses, BigDecimal savings) {}
  public record CategoryStat(String category, BigDecimal amount, long operations, BigDecimal average, BigDecimal share) {}
  public record MerchantStat(String merchant, BigDecimal amount, long operations, BigDecimal average, BigDecimal share) {}
  public record ProductStat(String productId, String name, Product.ProductType type, Product.Provider provider, BigDecimal balance, BigDecimal expenses, long operations, BigDecimal average) {}
  public record CalendarDay(LocalDate date, BigDecimal income, BigDecimal expenses, BigDecimal net, long operations) {}
  public record Insight(String severity, String code, String title, String detail, BigDecimal value) {}
  public record DashboardOverview(BigDecimal totalBalance, BigDecimal income, BigDecimal expenses, BigDecimal savings, BigDecimal savingsRate, BigDecimal averageDailyExpense, List<CategoryStat> categories, List<MerchantStat> merchants) {}

  public DashboardOverview overview(Period period, String productId) {
    var ms = included(period, productId);
    var income = income(ms); var expenses = expenses(ms); var savings = income.subtract(expenses);
    var days = Math.max(1, ChronoUnit.DAYS.between(period.from(), period.to()) + 1);
    var liquid = ledger.products().stream().filter(Product::liquid).map(Product::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new DashboardOverview(money(liquid), money(income), money(expenses), money(savings),
        income.signum() == 0 ? ZERO : savings.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP),
        expenses.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP), categories(period, productId), merchants(period, productId, 10));
  }

  public List<TrendPoint> trend(Period period, String productId, String groupBy) {
    var ms=included(period,productId);
    Function<Movement,LocalDate> bucket=switch(groupBy.toUpperCase(Locale.ROOT)){
      case "WEEK" -> m -> m.date().minusDays(m.date().getDayOfWeek().getValue()-1L);
      case "MONTH" -> m -> m.date().withDayOfMonth(1);
      case "YEAR" -> m -> m.date().withDayOfYear(1);
      default -> Movement::date;
    };
    return ms.stream().collect(Collectors.groupingBy(bucket,TreeMap::new,Collectors.toList())).entrySet().stream()
        .map(e->new TrendPoint(e.getKey(),money(income(e.getValue())),money(expenses(e.getValue())),money(income(e.getValue()).subtract(expenses(e.getValue()))))).toList();
  }

  public List<CategoryStat> categories(Period period,String productId){
    var expense=expenseMovements(included(period,productId)); var total=expenses(expense);
    return stats(expense, m->blank(m.category(),"Sin categoría"), total).stream()
        .map(s->new CategoryStat(s.key(),s.amount(),s.operations(),s.average(),s.share())).toList();
  }

  public List<MerchantStat> merchants(Period period,String productId,int limit){
    var expense=expenseMovements(included(period,productId)); var total=expenses(expense);
    return stats(expense,m->blank(m.merchant(),blank(m.description(),"Desconocido")),total).stream().limit(Math.min(Math.max(limit,1),100))
        .map(s->new MerchantStat(s.key(),s.amount(),s.operations(),s.average(),s.share())).toList();
  }

  public List<ProductStat> products(Period period){
    var byProduct=included(period,null).stream().collect(Collectors.groupingBy(Movement::productId));
    return ledger.products().stream().map(p->{var ms=byProduct.getOrDefault(p.id(),List.of());var exp=expenses(ms);long count=expenseMovements(ms).size();
      return new ProductStat(p.id(),p.name(),p.type(),p.provider(),money(p.balance()),money(exp),count,count==0?ZERO:exp.divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP));})
      .sorted(Comparator.comparing(ProductStat::expenses).reversed()).toList();
  }

  public List<CalendarDay> calendar(Period period,String productId){
    var by=included(period,productId).stream().collect(Collectors.groupingBy(Movement::date));
    return period.from().datesUntil(period.to().plusDays(1)).map(d->{var ms=by.getOrDefault(d,List.of());var in=income(ms);var ex=expenses(ms);
      return new CalendarDay(d,money(in),money(ex),money(in.subtract(ex)),ms.size());}).toList();
  }

  public List<Insight> insights(Period period,String productId){
    var result=new ArrayList<Insight>();var ms=included(period,productId);var in=income(ms);var ex=expenses(ms);var save=in.subtract(ex);
    if(in.signum()>0){var rate=save.multiply(BigDecimal.valueOf(100)).divide(in,2,RoundingMode.HALF_UP);result.add(new Insight(rate.signum()>=0?"POSITIVE":"WARNING","SAVINGS_RATE","Tasa de ahorro", "El ahorro representa el "+rate+" % de los ingresos.",rate));}
    categories(period,productId).stream().findFirst().ifPresent(c->result.add(new Insight("INFO","TOP_CATEGORY","Principal categoría de gasto",c.category()+" concentra el "+c.share()+" % del gasto.",c.amount())));
    merchants(period,productId,1).stream().findFirst().ifPresent(m->result.add(new Insight("INFO","TOP_MERCHANT","Comercio con mayor gasto",m.merchant()+" acumula "+m.amount()+" EUR en el período.",m.amount())));
    return result;
  }

  private List<Movement> included(Period p,String id){return ledger.movements(p,id).stream().filter(Movement::included).toList();}
  private List<Movement> expenseMovements(List<Movement> ms){return ms.stream().filter(m->m.amount().signum()<0||m.kind()==Movement.Kind.REFUND).toList();}
  private BigDecimal income(List<Movement> ms){return ms.stream().filter(m->m.amount().signum()>0&&m.kind()!=Movement.Kind.REFUND).map(Movement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
  private BigDecimal expenses(List<Movement> ms){return expenseMovements(ms).stream().map(m->m.kind()==Movement.Kind.REFUND?m.amount().negate():m.amount().abs()).reduce(BigDecimal.ZERO,BigDecimal::add);}
  private record Stat(String key,BigDecimal amount,long operations,BigDecimal average,BigDecimal share){}
  private List<Stat> stats(List<Movement> ms,Function<Movement,String> key,BigDecimal total){return ms.stream().collect(Collectors.groupingBy(key)).entrySet().stream().map(e->{var amount=expenses(e.getValue());long n=e.getValue().size();return new Stat(e.getKey(),money(amount),n,amount.divide(BigDecimal.valueOf(n),2,RoundingMode.HALF_UP),total.signum()==0?ZERO:amount.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP));}).sorted(Comparator.comparing(Stat::amount).reversed()).toList();}
  private BigDecimal money(BigDecimal n){return n==null?ZERO:n.setScale(2,RoundingMode.HALF_UP);}
  private String blank(String s,String fallback){return s==null||s.isBlank()?fallback:s;}
}