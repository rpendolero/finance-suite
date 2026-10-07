package com.finance.server.application.port;

/** Executes an application operation inside one atomic infrastructure transaction. */
public interface UnitOfWorkPort {

  <T> T execute(Work<T> work);

  @FunctionalInterface
  interface Work<T> {
    T run();
  }
}
