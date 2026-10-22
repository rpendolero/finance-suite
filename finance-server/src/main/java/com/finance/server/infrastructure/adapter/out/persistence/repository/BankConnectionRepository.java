package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.BankConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import com.finance.server.domain.banking.BankConnection;

public interface BankConnectionRepository extends JpaRepository<BankConnectionEntity, String> {
  Optional<BankConnectionEntity> findByAuthorizationState(String state);

  @Modifying
  @Query("update BankConnectionEntity c set c.lastSyncAt = :at where c.id = :id and c.externalSessionId = :sessionId and c.status = :active")
  int updateLastSyncIfActive(@Param("id") String id, @Param("sessionId") String sessionId,
      @Param("at") Instant at, @Param("active") BankConnection.Status active);

  @Modifying
  @Query("update BankConnectionEntity c set c.status = :expired where c.id = :id and c.externalSessionId = :sessionId and c.status = :active")
  int requireReauthorizationIfSessionMatches(@Param("id") String id, @Param("sessionId") String sessionId,
      @Param("active") BankConnection.Status active, @Param("expired") BankConnection.Status expired);
}
