package com.finance.server.infrastructure.adapter.out.persistence.entity;

import com.finance.server.domain.banking.BankConnection;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "bank_connection")
@Getter @Setter @NoArgsConstructor
public class BankConnectionEntity {
  @Id @Column(length = 36) private String id;
  @Column(nullable = false, length = 32) private String provider;
  @Column(name = "bank_name", nullable = false, length = 100) private String bankName;
  @Column(nullable = false, length = 2) private String country;
  @Column(name = "external_session_id", length = 160) private String externalSessionId;
  @Column(name = "authorization_state", nullable = false, unique = true, length = 64) private String authorizationState;
  @Column(name = "valid_until") private Instant validUntil;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private BankConnection.Status status;
  @Column(name = "last_sync_at") private Instant lastSyncAt;
}
