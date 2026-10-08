package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.ExternalBankAccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalBankAccountRepository extends JpaRepository<ExternalBankAccountEntity, String> {
  List<ExternalBankAccountEntity> findByConnectionIdOrderByNameAsc(String connectionId);
}
