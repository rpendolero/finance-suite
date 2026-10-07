package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.server.application.port.BankingAuthorizationPort;
import com.finance.server.infrastructure.config.EnableBankingProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EnableBankingAuthorizationAdapter implements BankingAuthorizationPort {
  private final URI base;
  private final String redirectUrl;
  private final ObjectMapper mapper;
  private final EnableBankingJwtProvider jwt;
  private final HttpClient http;

  public EnableBankingAuthorizationAdapter(EnableBankingProperties properties, ObjectMapper mapper) {
    if (!properties.getBaseUrl().startsWith("https://")) throw new IllegalArgumentException("Enable Banking API requires HTTPS");
    this.base = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/");
    this.redirectUrl = properties.getRedirectUrl();
    this.mapper = mapper;
    this.jwt = EnableBankingJwtProvider.of(properties.getApplicationId(), properties.getPrivateKey(), mapper);
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override
  public Authorization start(String bankName, String country, String state, OffsetDateTime validUntil) {
    JsonNode response = post("auth", Map.of(
        "access", Map.of("valid_until", validUntil.toString()),
        "aspsp", Map.of("name", bankName, "country", country),
        "state", state,
        "redirect_url", redirectUrl,
        "psu_type", "personal"));
    String url = requiredText(response, "url");
    return new Authorization(url);
  }

  @Override
  public Session exchangeCode(String code) {
    JsonNode response = post("sessions", Map.of("code", code));
    return new Session(requiredText(response, "session_id"));
  }

  public JsonNode aspsps(String country) { return get("aspsps?country=" + URLEncoder.encode(country, StandardCharsets.UTF_8)); }
  public JsonNode session(String sessionId) { return get("sessions/" + encodePath(sessionId)); }
  public JsonNode account(String accountId) { return get("accounts/" + encodePath(accountId)); }
  public JsonNode balances(String accountId) { return get("accounts/" + encodePath(accountId) + "/balances"); }
  public JsonNode transactions(String accountId) { return get("accounts/" + encodePath(accountId) + "/transactions"); }

  private JsonNode get(String path) {
    return send(HttpRequest.newBuilder(base.resolve(path)).GET().build());
  }

  private JsonNode post(String path, Object body) {
    try {
      return send(HttpRequest.newBuilder(base.resolve(path))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build());
    } catch (Exception e) {
      throw new IllegalStateException("Could not encode Enable Banking request", e);
    }
  }

  private JsonNode send(HttpRequest prototype) {
    HttpRequest request = HttpRequest.newBuilder(prototype.uri()).timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json").header("Authorization", "Bearer " + jwt.token())
        .method(prototype.method(), prototype.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Enable Banking request rejected: status={}", response.statusCode());
        throw new IllegalStateException("Enable Banking request failed: HTTP " + response.statusCode());
      }
      return mapper.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Enable Banking request interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException("Enable Banking request failed", e);
    }
  }

  private String requiredText(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) throw new IllegalStateException("Enable Banking response is missing " + field);
    return value;
  }

  private String encodePath(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Enable Banking id is required");
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
