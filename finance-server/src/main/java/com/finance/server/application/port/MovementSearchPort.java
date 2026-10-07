package com.finance.server.application.port;

import com.finance.domain.Movement;
import com.finance.domain.Period;
import java.math.BigDecimal;
import java.util.List;

public interface MovementSearchPort {
  Page search(Criteria criteria);

  record Criteria(
      Period period,
      String productId,
      String category,
      String merchant,
      String text,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      Movement.Kind kind,
      Movement.Status status,
      SortField sortBy,
      SortDirection sortDirection,
      int offset,
      int limit) {
    public enum SortField { DATE, AMOUNT, MERCHANT, CATEGORY }
    public enum SortDirection { ASC, DESC }
  }

  record Page(List<Movement> items, long total, int offset, int limit) {}
}
