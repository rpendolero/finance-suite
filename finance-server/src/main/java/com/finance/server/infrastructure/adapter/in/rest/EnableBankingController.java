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

  @GetMapping("/banks")
  public java.util.List<String> banks() { return authorizations.banks(properties.getCountry()); }

  @PostMapping("/authorizations")
  public ResponseEntity<AuthorizationResponse> authorize(@jakarta.validation.Valid @RequestBody AuthorizationRequest request) {
    var result = authorizations.start(request.getBankName(), properties.getCountry(), properties.getConsentDays());
    return ResponseEntity.created(URI.create(result.authorizationUrl()))
        .body(new AuthorizationResponse(result.connectionId(), result.authorizationUrl(), result.state()));
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
      @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
    if (error != null) return returnToFrontend("cancelled");
    if (code == null || code.isBlank()) throw new IllegalArgumentException("Authorization code is required");
    authorizations.complete(code, state);
    return returnToFrontend("connected");
  }

  private ResponseEntity<Void> returnToFrontend(String status) {
    URI target = org.springframework.web.util.UriComponentsBuilder.fromUriString(properties.getFrontendUrl())
        .replaceQueryParam("banking", status).build().toUri();
    return ResponseEntity.status(303).location(target).build();
  }

  @Data
  public static class AuthorizationRequest {
    @NotBlank private String bankName;
  }

  public record AuthorizationResponse(String connectionId, String authorizationUrl, String state) {}
  public record SessionResponse(String connectionId, String status) {}
}
