package com.finance.server.infrastructure.adapter.out.persistence;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/** Runs outside transaction advice so failures during flush/commit are also traced. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class DatabaseErrorTracing {
  @Around("execution(public * com.finance.server.infrastructure.adapter.out.persistence.Jpa*Adapter.*(..))")
  public Object trace(ProceedingJoinPoint operation) throws Throwable {
    try {
      return operation.proceed();
    } catch (DataAccessException | TransactionException | PersistenceException error) {
      var failure = DatabaseFailure.from(error);
      log.error("Database operation failed: operation={}, code={}, errorType={}, sqlState={}, vendorCode={}, correlationId={}",
          operation.getSignature().toShortString(), failure.code(), error.getClass().getSimpleName(),
          failure.sqlState(), failure.vendorCode(), MDC.get("correlationId"), error);
      // Preserve the original exception and rollback behavior; never return a false success.
      throw error;
    }
  }
}
