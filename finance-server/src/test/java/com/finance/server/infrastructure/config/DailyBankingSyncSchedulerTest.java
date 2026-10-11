package com.finance.server.infrastructure.config;

import static org.mockito.Mockito.*;

import com.finance.server.application.service.BankingSyncService;
import com.finance.server.domain.banking.BankConnection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyBankingSyncSchedulerTest {
  private final BankingSyncService service = mock(BankingSyncService.class);
  private final Instant now = Instant.parse("2026-10-11T04:00:00Z");
  private final DailyBankingSyncScheduler scheduler =
      new DailyBankingSyncScheduler(service, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void synchronizesOnlyActiveAuthorizedConnectionsAndContinuesAfterFailures() {
    var active = connection("active", BankConnection.Status.ACTIVE, now.plusSeconds(86400));
    var expired = connection("expired", BankConnection.Status.ACTIVE, now.minusSeconds(1));
    var unauthorized = connection("unauthorized", BankConnection.Status.AUTHORIZING, now.plusSeconds(86400));
    var next = connection("next", BankConnection.Status.ACTIVE, now.plusSeconds(86400));
    when(service.connections()).thenReturn(List.of(active, expired, unauthorized, next));
    when(service.sync("active")).thenThrow(new IllegalStateException("Temporary provider error"));
    when(service.sync("next")).thenReturn(new BankingSyncService.SyncResult(1, 1, 0));

    scheduler.synchronize();

    verify(service).sync("active");
    verify(service).sync("next");
    verify(service, never()).sync("expired");
    verify(service, never()).sync("unauthorized");
  }

  private BankConnection connection(String id, BankConnection.Status status, Instant validUntil) {
    return new BankConnection(id, "ENABLE_BANKING", "Example", "ES", "session",
        "state", validUntil, status, null);
  }
}
