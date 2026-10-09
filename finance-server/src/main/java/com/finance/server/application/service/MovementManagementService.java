package com.finance.server.application.service;

import com.finance.server.application.port.LedgerPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MovementManagementService {
  private final LedgerPort ledger;

  public void delete(String id) {
    ledger.deleteMovement(id);
  }
}
