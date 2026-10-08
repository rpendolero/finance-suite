package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.server.application.service.BankingSyncService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banking/connections")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finance.enable-banking", name = "enabled", havingValue = "true")
public class BankingSyncController {
  private final BankingSyncService sync;

  @PostMapping("/{connectionId}/discover")
  public List<?> discover(@PathVariable String connectionId) { return sync.discoverAccounts(connectionId); }

  @PostMapping("/{connectionId}/accounts/link")
  public Object link(@PathVariable String connectionId, @RequestBody LinkRequest request) {
    return sync.link(connectionId, request.getExternalAccountId(), request.getProductId());
  }

  @PostMapping("/{connectionId}/sync")
  public BankingSyncService.SyncResult synchronize(@PathVariable String connectionId) { return sync.sync(connectionId); }

  @Data
  public static class LinkRequest {
    @NotBlank private String externalAccountId;
    @NotBlank private String productId;
  }
}
