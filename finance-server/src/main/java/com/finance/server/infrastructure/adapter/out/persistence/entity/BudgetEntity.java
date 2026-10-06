package com.finance.server.infrastructure.adapter.out.persistence.entity;

import com.finance.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "budget")
@IdClass(BudgetId.class)
@Getter
@Setter
@NoArgsConstructor
public class BudgetEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "month_code", length = 7)
  private String month;

  @Id
  @Column(length = 64)
  private String category;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;
}
