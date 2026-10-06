package com.finance.server.infrastructure.adapter.in.mcp;

import com.finance.domain.Period;
import com.finance.server.application.service.ExtendedAnalysisService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExtendedTools {
  private final ExtendedAnalysisService analysis;

  private Period p(String from, String to) {
    return new Period(LocalDate.parse(from), LocalDate.parse(to));
  }

  @Tool(
      name = "bank_get_monthly_trend",
      description =
          "Evolución de ingresos, gastos, ahorro y categorías; meses YYYY-MM, máximo 36 meses. Los"
              + " meses sin datos no implican gasto cero real.")
  public Object trend(String fromMonth, String toMonth) {
    return analysis.trend(fromMonth, toMonth);
  }

  @Tool(
      name = "bank_get_merchant_spending",
      description =
          "Gasto neto de devoluciones por comercio para fechas ISO. UNKNOWN agrupa comercios sin"
              + " identificar.")
  public Object merchants(String from, String to) {
    return analysis.merchants(p(from, to));
  }

  @Tool(
      name = "bank_get_budget_status",
      description =
          "Presupuestos previamente configurados para mes YYYY-MM y gasto consumido. No proyecta"
              + " gasto restante del mes.")
  public Object budgets(String month) {
    return analysis.budgets(month);
  }

  @Tool(
      name = "bank_get_reconciliation_candidates",
      description =
          "Pares candidatos a transferencias internas o pagos de tarjeta; no modifica datos."
              + " Revisar antes de excluir para evitar doble conteo.")
  public Object pairs(String from, String to) {
    return analysis.reconciliation(p(from, to));
  }

  @Tool(
      name = "bank_get_recurring_increases",
      description =
          "Subidas del último cargo frente al anterior en pagos mensuales candidatos; confirmar"
              + " condiciones y cobertura.")
  public Object increases(String from, String to) {
    return analysis.increases(p(from, to));
  }

  @Tool(
      name = "bank_get_account_cash_flow",
      description =
          "Flujo efectivo de cuentas, incluye liquidaciones de tarjetas y transferencias. Diferente"
              + " del gasto económico por compras.")
  public Object cashFlow(String from, String to) {
    return analysis.cashFlow(p(from, to));
  }
}
