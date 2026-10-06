package com.finance.server.infrastructure.adapter.in.mcp;

import com.finance.domain.*;
import com.finance.server.application.port.FinanceQueries;
import com.finance.server.application.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinancialReportTools {
  private final FinanceQueries queries;
  private final ExtendedAnalysisService analysis;

  @Tool(
      name = "bank_get_financial_report",
      description =
          "Informe consolidado de cuentas y tarjetas: calidad, saldo neto declarado, resumen del"
              + " periodo, gasto por comercio, deuda, recurrentes, subidas, anomalías y flujo de"
              + " cuentas. Fechas ISO. Conclusiones condicionadas a cobertura y clasificación.")
  public Object report(String from, String to) {
    var p = new com.finance.domain.Period(LocalDate.parse(from), LocalDate.parse(to));
    var products = queries.products();
    BigDecimal net =
        products.stream()
            .filter(x -> x.type() != Product.ProductType.DEBIT_CARD)
            .map(Product::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var result = new LinkedHashMap<String, Object>();
    result.put("quality", queries.quality(p));
    var entities = new LinkedHashMap<String, Object>();
    for (var provider : Product.Provider.values())
      entities.put(provider.name(), queries.providerSummary(p, provider));
    result.put("providers", entities);
    result.put("declaredNetBankPosition", net);
    result.put(
        "positionBasis",
        "Suma de cuentas, monederos PayPal y saldos de tarjetas de crédito; débito excluido para"
            + " evitar duplicar cuentas. Saldos de distinta fecha, no incluye inversiones/préstamos"
            + " externos.");
    result.put("summary", queries.summary(p, null));
    result.put("merchantSpending", analysis.merchants(p));
    result.put("cardExposure", queries.cards());
    result.put("recurringCandidates", queries.recurring(p));
    result.put("recurringIncreases", analysis.increases(p));
    result.put("anomalies", queries.anomalies(p));
    result.put("reconciliationCandidates", analysis.reconciliation(p));
    result.put("accountCashFlow", analysis.cashFlow(p));
    return result;
  }
}
