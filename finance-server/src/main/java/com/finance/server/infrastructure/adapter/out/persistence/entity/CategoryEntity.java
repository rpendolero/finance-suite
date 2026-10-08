package com.finance.server.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
public class CategoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
  @OrderBy("displayOrder ASC, id ASC")
  private List<SubcategoryEntity> subcategories = new ArrayList<>();
}
