package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.ErrorResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class EnableBankingApiClient {
  private final URI base;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final Supplier<String> token;

  public <T> T get(String path, Class<T> responseType) {
    return send(request(path).GET().build(), responseType);
  }

  public <T> T post(String path, Object body, Class<T> responseType) {
    final String payload;
    try {
      payload = mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not encode Enable Banking request", e);
    }
    HttpRequest request = request(path).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
    return send(request, responseType);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + token.get());
  }

  private <T> T send(HttpRequest request, Class<T> responseType) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String code = errorCode(response.body());
        log.warn("Enable Banking request rejected: method={}, status={}, code={}", request.method(), response.statusCode(), code);
        throw new IllegalStateException("Enable Banking request failed: HTTP " + response.statusCode() + ", code=" + code);
      }
      T value = mapper.readValue(response.body(), responseType);
      if (value == null) throw new IllegalStateException("Empty Enable Banking response");
      return value;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Enable Banking request interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read Enable Banking response", e);
    }
  }

  private String errorCode(String body) {
    try {
      ErrorResponse error = mapper.readValue(body, ErrorResponse.class);
      String code = error == null ? null : error.getError();
      return code != null && code.matches("[A-Z0-9_]{1,80}") ? code : "UNKNOWN";
    } catch (JsonProcessingException e) {
      return "UNKNOWN";
    }
  }
}
