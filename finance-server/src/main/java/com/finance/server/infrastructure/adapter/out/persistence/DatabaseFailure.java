package com.finance.server.infrastructure.adapter.out.persistence;

import java.util.*;
import java.sql.SQLException;
import org.springframework.dao.*;

/** Technical classification without exposing database messages to API clients. */
public record DatabaseFailure(String code, int status, String sqlState, Integer vendorCode) {
  public static DatabaseFailure from(Throwable error) {
    String code = "DATABASE_ERROR";
    int status = 500;
    String state = null;
    Integer vendor = null;
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (cause instanceof SQLException sql) {
        state = sql.getSQLState(); vendor = sql.getErrorCode();
        if (state != null && state.startsWith("23")) { code = "DATABASE_CONSTRAINT"; status = 409; }
        else if (status == 500 && state != null && (state.startsWith("08") || state.startsWith("40"))) {
          code = "DATABASE_UNAVAILABLE"; status = 503;
        }
      }
      if (cause instanceof DataIntegrityViolationException) { code = "DATABASE_CONSTRAINT"; status = 409; }
      else if (status != 409 && (cause instanceof OptimisticLockingFailureException || cause instanceof jakarta.persistence.OptimisticLockException)) {
        code = "DATABASE_CONCURRENT_UPDATE"; status = 409;
      } else if (status == 500 && (cause instanceof TransientDataAccessException
          || cause instanceof DataAccessResourceFailureException
          || cause instanceof org.springframework.transaction.CannotCreateTransactionException
          || cause instanceof jakarta.persistence.LockTimeoutException
          || cause instanceof jakarta.persistence.PessimisticLockException)) {
        code = "DATABASE_UNAVAILABLE"; status = 503;
      }
    }
    return new DatabaseFailure(code, status, state, vendor);
  }
}
