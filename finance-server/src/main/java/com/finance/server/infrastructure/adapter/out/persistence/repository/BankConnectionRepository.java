package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.BankConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConnectionRepository extends JpaRepository<BankConnectionEntity, String> {
  Optional<BankConnectionEntity> findByAuthorizationState(String state);
}
