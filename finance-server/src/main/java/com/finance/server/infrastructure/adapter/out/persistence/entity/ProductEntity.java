package com.finance.server.infrastructure.adapter.out.persistence.entity;

import com.finance.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product", uniqueConstraints =
    @UniqueConstraint(name = "uk_product_provider_external", columnNames = {"provider", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {
  @Column(name = "external_id", length = 160)
  private String externalId;

  @Column(name = "masked_pan", length = 9)
  private String maskedPan;

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  private Product.ProductType type;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal balance;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "balance_at", nullable = false)
  private Instant balanceAt;

  @Column(name = "linked_account_id", length = 64)
  private String linkedAccountId;

  @Column(name = "credit_limit", precision = 18, scale = 2)
  private BigDecimal creditLimit;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  private Product.Provider provider;
}
