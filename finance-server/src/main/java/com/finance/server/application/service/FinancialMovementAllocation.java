package com.finance.server.application.service;

import com.finance.domain.Movement;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

/**
 * Uses the sign of computable booked movements for income and expense totals.
 * Saving and financing are additional breakdowns, already included in those totals.
 * Never estimates the principal/interest split of an unitemized loan payment.
 */
public final class FinancialMovementAllocation {

  private FinancialMovementAllocation() {}

  public static boolean saving(Movement movement) {
    return movement.included() && isCategory(movement, "AHORRO_E_INVERSION", "AHORRO_INVERSION", "AHORRO_Y_INVERSION");
  }

  public static boolean financing(Movement movement) {
    return movement.included() && isCategory(movement, "FINANCIACION");
  }

  public static boolean refund(Movement movement) {
    return movement.kind() == Movement.Kind.REFUND
        || (isCategory(movement, "INGRESOS") && "DEVOLUCIONES".equalsIgnoreCase(movement.subcategory())
            && movement.amount().signum() > 0);
  }

  public static boolean income(Movement movement) {
    return movement.included() && movement.amount().signum() > 0;
  }

  public static boolean expense(Movement movement) {
    return movement.included() && movement.amount().signum() < 0;
  }

  /** Expresses debits as positive outflows and credits as negative allocations. */
  public static BigDecimal paid(Movement movement) {
    return movement.amount().negate();
  }

  public static boolean financingCapital(Movement movement) {
    return financing(movement) && "AMORTIZACION_CAPITAL".equalsIgnoreCase(movement.subcategory());
  }

  public static boolean financingInterest(Movement movement) {
    return financing(movement) && "INTERESES".equalsIgnoreCase(movement.subcategory());
  }

  public static BigDecimal total(List<Movement> movements, Predicate<Movement> filter, boolean negate) {
    BigDecimal sum = movements.stream().filter(filter).map(Movement::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return negate ? sum.negate() : sum;
  }

  private static boolean isCategory(Movement movement, String... codes) {
    if (movement.category() == null) return false;
    for (String code : codes) {
      if (code.equalsIgnoreCase(movement.category())) return true;
    }
    return false;
  }
}
