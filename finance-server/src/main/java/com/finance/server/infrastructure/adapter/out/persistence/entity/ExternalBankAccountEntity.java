package com.finance.server.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "external_bank_account", uniqueConstraints = @UniqueConstraint(name = "uk_external_bank_account", columnNames = {"connection_id","external_account_id"}))
@Getter @Setter @NoArgsConstructor
public class ExternalBankAccountEntity {
  @Id @Column(length = 36) private String id;
  @Column(name = "connection_id", nullable = false, length = 36) private String connectionId;
  @Column(name = "external_account_id", nullable = false, length = 160) private String externalAccountId;
  @Column(name = "product_id", length = 64) private String productId;
  @Column(nullable = false, length = 100) private String name;
  @Column(nullable = false, length = 3) private String currency;
}
