package com.finance.server.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subcategory")
@Getter
@Setter
@NoArgsConstructor
public class SubcategoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private CategoryEntity category;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;
}
