package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finance.server.infrastructure.adapter.in.rest.ApiErrors;
import com.finance.server.infrastructure.adapter.out.persistence.*;
import com.finance.server.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.finance.server.infrastructure.adapter.out.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.*;
import org.springframework.transaction.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import java.sql.SQLException;

class DatabaseErrorHandlingTest {
  @Test void reportsConstraintWithoutExposingDatabaseDetails() {
    MDC.put("correlationId", "test-correlation");
    try {
      var problem = new ApiErrors().persistence(new DataIntegrityViolationException("private-bank-data",
          new SQLException("secret SQL", "23000", 1062)));
      assertThat(problem.getStatus()).isEqualTo(409);
      assertThat(problem.getProperties()).containsEntry("code", "DATABASE_CONSTRAINT")
          .containsEntry("correlationId", "test-correlation");
      assertThat(problem.getDetail()).doesNotContain("private-bank-data", "secret SQL");
    } finally { MDC.remove("correlationId"); }
  }

  @Test void classifiesConnectionTimeoutConcurrencyAndUnknownFailures() {
    assertThat(DatabaseFailure.from(new DataAccessResourceFailureException("offline")).status()).isEqualTo(503);
    assertThat(DatabaseFailure.from(new QueryTimeoutException("timeout")).status()).isEqualTo(503);
    assertThat(DatabaseFailure.from(new OptimisticLockingFailureException("version")).status()).isEqualTo(409);
    assertThat(DatabaseFailure.from(new CannotCreateTransactionException("connection")).status()).isEqualTo(503);
    var wrapped = DatabaseFailure.from(new TransactionSystemException("commit",
        new SQLException("connection", "08006", 7)));
    assertThat(wrapped.status()).isEqualTo(503);
    assertThat(wrapped.sqlState()).isEqualTo("08006");
    assertThat(wrapped.vendorCode()).isEqualTo(7);
    assertThat(DatabaseFailure.from(new InvalidDataAccessResourceUsageException("bad query")).status()).isEqualTo(500);
  }

  @Test void tracingAlsoCatchesCommitFailure() {
    var repository = mock(ClassificationRuleRepository.class);
    when(repository.findAllByOrderByPriorityAscIdAsc()).thenReturn(java.util.List.of());
    var target = new JpaSettingsAdapter(repository, mock(BudgetRepository.class), mock(PersistenceMapper.class));
    var factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new DatabaseErrorTracing());
    var manager = mock(PlatformTransactionManager.class);
    var status = new org.springframework.transaction.support.SimpleTransactionStatus();
    when(manager.getTransaction(any())).thenReturn(status);
    var original = new TransactionSystemException("commit failed", new SQLException("offline", "08006"));
    doThrow(original).when(manager).commit(status);
    var attributes = new org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("*", new org.springframework.transaction.interceptor.RuleBasedTransactionAttribute());
    factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager, attributes));
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DatabaseErrorTracing.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start(); logger.addAppender(appender);
    try {
      JpaSettingsAdapter proxy = factory.getProxy();
      assertThatThrownBy(proxy::rules).isSameAs(original);
      assertThat(appender.list).singleElement().satisfies(event ->
          assertThat(event.getFormattedMessage()).contains("JpaSettingsAdapter.rules()", "08006"));
    } finally { logger.detachAppender(appender); appender.stop(); }
  }

  @Test void adapterTracingPreservesOriginalExceptionAndRecordsOperation() {
    var repository = mock(ClassificationRuleRepository.class);
    var original = new DataAccessResourceFailureException("offline");
    when(repository.findAllByOrderByPriorityAscIdAsc()).thenThrow(original);
    var target = new JpaSettingsAdapter(repository, mock(BudgetRepository.class), mock(PersistenceMapper.class));
    var factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new DatabaseErrorTracing());
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DatabaseErrorTracing.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start(); logger.addAppender(appender);
    try {
      JpaSettingsAdapter proxy = factory.getProxy();
      assertThatThrownBy(proxy::rules).isSameAs(original);
      assertThat(appender.list).singleElement().satisfies(event -> {
        assertThat(event.getFormattedMessage()).contains("JpaSettingsAdapter.rules()", "DATABASE_UNAVAILABLE");
        assertThat(event.getThrowableProxy()).isNotNull();
      });
    } finally { logger.detachAppender(appender); appender.stop(); }
  }
}
