package com.finance.server.infrastructure.adapter.out.persistence.entity;

import com.finance.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "movement",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_product_external",
            columnNames = {"product_id", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MovementEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36)
  private String id;

  @Column(name = "product_id", nullable = false, length = 64)
  private String productId;

  @Column(name = "external_id", nullable = false, length = 160)
  private String externalId;

  @Column(name = "booking_date", nullable = false)
  private LocalDate bookingDate;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 1000)
  private String description;

  @Column(length = 200)
  private String merchant;

  @Column(nullable = false, length = 64)
  private String category;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 24)
  private Movement.Kind kind;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  private Movement.Status status;
}
