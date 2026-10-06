package com.finance.server.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetId implements Serializable {
  private String month;
  private String category;
}
