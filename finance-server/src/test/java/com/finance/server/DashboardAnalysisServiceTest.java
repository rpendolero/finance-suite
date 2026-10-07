package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finance.domain.*;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.service.DashboardAnalysisService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardAnalysisServiceTest {
 @Test void calculatesOverviewTrendCategoriesMerchantsAndCalendar(){
  var ledger=mock(LedgerPort.class);var p=new Period(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2));
  when(ledger.products()).thenReturn(List.of(new Product("a","Cuenta",Product.ProductType.ACCOUNT,"EUR",new BigDecimal("1000"),Instant.now(),null,null)));
  when(ledger.movements(p,null)).thenReturn(List.of(
   new Movement("1","a","1",p.from(),new BigDecimal("2000"),"EUR","Nomina","Empresa","INGRESOS",Movement.Kind.NORMAL,Movement.Status.BOOKED),
   new Movement("2","a","2",p.from(),new BigDecimal("-50"),"EUR","Compra","Mercado","ALIMENTACION",Movement.Kind.NORMAL,Movement.Status.BOOKED),
   new Movement("3","a","3",p.to(),new BigDecimal("-25"),"EUR","Compra","Mercado","ALIMENTACION",Movement.Kind.NORMAL,Movement.Status.BOOKED)));
  var s=new DashboardAnalysisService(ledger);var o=s.overview(p,null);
  assertThat(o.totalBalance()).isEqualByComparingTo("1000");assertThat(o.income()).isEqualByComparingTo("2000");
  assertThat(o.expenses()).isEqualByComparingTo("75");assertThat(o.savings()).isEqualByComparingTo("1925");
  assertThat(s.trend(p,null,"DAY")).hasSize(2);assertThat(s.categories(p,null).getFirst().operations()).isEqualTo(2);
  assertThat(s.merchants(p,null,10).getFirst().merchant()).isEqualTo("Mercado");assertThat(s.calendar(p,null)).hasSize(2);
 }
}