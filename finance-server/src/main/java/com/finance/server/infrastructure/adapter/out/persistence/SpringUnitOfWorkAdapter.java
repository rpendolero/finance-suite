package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.server.application.port.UnitOfWorkPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public final class SpringUnitOfWorkAdapter implements UnitOfWorkPort {

  private final PlatformTransactionManager transactionManager;

  @Override
  public <T> T execute(Work<T> work) {
    return new TransactionTemplate(transactionManager).execute(status -> work.run());
  }
}
