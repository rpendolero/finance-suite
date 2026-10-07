package com.finance.importer.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.importer.infrastructure.config.EnableBankingProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

public final class EnableBankingClient {
  private final URI base;
  private final String redirectUrl;
  private final ObjectMapper mapper;
  private final EnableBankingJwtProvider jwt;
  private final HttpClient http;

  public EnableBankingClient(EnableBankingProperties properties, ObjectMapper mapper) {
    if (!properties.getBaseUrl().startsWith("https://")) throw new IllegalArgumentException("Enable Banking API requires HTTPS");
    this.base = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/");
    this.redirectUrl = properties.getRedirectUrl();
    this.mapper = mapper;
    this.jwt = new EnableBankingJwtProvider(properties.getApplicationId(), properties.getPrivateKey(), mapper);
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  public JsonNode aspsps(String country) { return get("aspsps?country=" + country); }

  public JsonNode startAuthorization(String bankName, String country, String state, OffsetDateTime validUntil) {
    return post("auth", Map.of("access", Map.of("valid_until", validUntil.toString()), "aspsp", Map.of("name", bankName, "country", country), "state", state, "redirect_url", redirectUrl, "psu_type", "personal"));
  }

  public JsonNode createSession(String code) { return post("sessions", Map.of("code", code)); }
  public JsonNode session(String sessionId) { return get("sessions/" + safeId(sessionId)); }
  public JsonNode account(String accountId) { return get("accounts/" + safeId(accountId)); }
  public JsonNode balances(String accountId) { return get("accounts/" + safeId(accountId) + "/balances"); }
  public JsonNode transactions(String accountId) { return get("accounts/" + safeId(accountId) + "/transactions"); }

  private JsonNode get(String path) {
    return send(HttpRequest.newBuilder(base.resolve(path)).GET().build());
  }

  private JsonNode post(String path, Object body) {
    try {
      return send(HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not encode Enable Banking request", e);
    }
  }

  private JsonNode send(HttpRequest prototype) {
    HttpRequest request = HttpRequest.newBuilder(prototype.uri()).timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json").header("Authorization", "Bearer " + jwt.token())
        .method(prototype.method(), prototype.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("Enable Banking request failed: HTTP " + response.statusCode());
      return mapper.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Enable Banking request interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("Enable Banking request failed", e);
    }
  }

  private String safeId(String value) {
    if (value == null || !value.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid Enable Banking id");
    return value;
  }
}
