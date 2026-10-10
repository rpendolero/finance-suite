package com.finance.server.infrastructure.config;

import com.finance.server.application.service.BankingSyncService;
import com.finance.server.domain.banking.BankConnection;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the same application use case used by manual bank synchronization. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "finance.enable-banking", name = "enabled", havingValue = "true")
public class DailyBankingSyncScheduler {
  private final BankingSyncService synchronization;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();

  @Scheduled(cron = "${finance.enable-banking.sync.cron:0 0 6 * * *}",
             zone = "${finance.enable-banking.sync.zone:Europe/Madrid}")
  @ConditionalOnProperty(prefix = "finance.enable-banking.sync", name = "enabled",
                         havingValue = "true", matchIfMissing = true)
  public void synchronize() {
    if (!running.compareAndSet(false, true)) {
      log.warn("Scheduled bank synchronization skipped: previous execution is still running");
      return;
    }
    try {
      synchronizeConnections();
    } finally {
      running.set(false);
    }
  }

  void synchronizeConnections() {
    Instant now = clock.instant();
    int successful = 0;
    int failed = 0;
    int skipped = 0;
    for (BankConnection connection : synchronization.connections()) {
      if (!eligible(connection, now)) {
        skipped++;
        continue;
      }
      try {
        var result = synchronization.sync(connection.id());
        successful++;
        log.info("Scheduled bank synchronization successful: connectionId={}, read={}, inserted={}, duplicates={}",
            connection.id(), result.read(), result.inserted(), result.duplicates());
      } catch (Exception exception) {
        failed++;
        log.error("Scheduled bank synchronization failed: connectionId={}, errorType={}, message={}",
            connection.id(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
      }
    }
    log.info("Scheduled bank synchronization finished: successful={}, failed={}, skipped={}",
        successful, failed, skipped);
  }

  private boolean eligible(BankConnection connection, Instant now) {
    if (connection.status() != BankConnection.Status.ACTIVE
        || connection.externalSessionId() == null || connection.externalSessionId().isBlank()
        || connection.validUntil() == null || !connection.validUntil().isAfter(now)) {
      log.info("Scheduled synchronization skipped: connectionId={}, status={}",
          connection.id(), connection.status());
      return false;
    }
    if (connection.validUntil().isBefore(now.plus(7, ChronoUnit.DAYS))) {
      log.warn("Bank consent expires soon: connectionId={}, validUntil={}",
          connection.id(), connection.validUntil());
    }
    return true;
  }
}
