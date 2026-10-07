package com.finance.server.infrastructure.adapter.out.persistence.entity;

import com.finance.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "classification_rule")
@Getter
@Setter
@NoArgsConstructor
public class ClassificationRuleEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false)
  private int priority;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "match_type", nullable = false, length = 16)
  private ClassificationRule.MatchType matchType;

  @Column(name = "contains_text", nullable = false, length = 200)
  private String contains;

  @Column(nullable = false, length = 64)
  private String category;

  @Column(length = 64)
  private String subcategory;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 24)
  private Movement.Kind kind;

  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal confidence;
}
