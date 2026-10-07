package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.server.application.service.BankingAuthorizationService;
import com.finance.server.infrastructure.config.EnableBankingProperties;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banking/enable-banking")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "finance.enable-banking", name = "enabled", havingValue = "true")
public class EnableBankingController {
  private final BankingAuthorizationService authorizations;
  private final EnableBankingProperties properties;

  @PostMapping("/authorizations")
  public ResponseEntity<AuthorizationResponse> authorize(@RequestBody AuthorizationRequest request) {
    var result = authorizations.start(request.getBankName(), properties.getCountry(), properties.getConsentDays());
    return ResponseEntity.created(URI.create(result.authorizationUrl()))
        .body(new AuthorizationResponse(result.connectionId(), result.authorizationUrl(), result.state()));
  }

  @GetMapping("/callback")
  public ResponseEntity<SessionResponse> callback(@RequestParam @NotBlank String code, @RequestParam(required = false) String state) {
    var connection = authorizations.complete(code, state);
    return ResponseEntity.ok(new SessionResponse(connection.id(), connection.status().name()));
  }

  @Data
  public static class AuthorizationRequest {
    @NotBlank private String bankName;
  }

  public record AuthorizationResponse(String connectionId, String authorizationUrl, String state) {}
  public record SessionResponse(String connectionId, String status) {}
}
