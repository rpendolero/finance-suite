package com.finance.importer.infrastructure.adapter.out.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.domain.Product;
import com.finance.importer.application.port.IngestionPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Slf4j
public class HttpIngestionAdapter implements IngestionPort {
  private final URI base;
  private final String authorization;
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final MultipartBatchEncoder encoder;

  public HttpIngestionAdapter(String baseUrl, String password, ObjectMapper mapper) {
    this.base = validateBaseUrl(baseUrl);
    if (password == null || password.length() < 20)
      throw new IllegalArgumentException(
          "Credencial de importación de al menos 20 caracteres requerida");
    this.authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(("importer:" + password).getBytes(StandardCharsets.UTF_8));
    this.mapper = mapper;
    this.encoder = new MultipartBatchEncoder(mapper);
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  static URI validateBaseUrl(String value) {
    URI uri = URI.create(value);
    String host = uri.getHost();
    boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(host == null ? "" : host);
    if (host == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && local))
      throw new IllegalArgumentException(
          "Servidor remoto requiere HTTPS sin credenciales en URL; HTTP permitido solo en"
              + " loopback");
    return URI.create(value.endsWith("/") ? value : value + "/");
  }

  @Override
  public Result upload(String productId, Path csv, Product snapshot) {
    if (productId == null || !productId.matches("[a-zA-Z0-9_-]{1,64}"))
      throw new IllegalArgumentException("Id inválido");
    log.info("Batch upload started");
    try {
      var request = buildRequest(productId, encoder.encode(csv, snapshot));
      var response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      log.info("Batch upload response received: status={}", response.statusCode());
      return decodeResponse(response);
    } catch (InterruptedException e) {
      log.warn("Batch upload interrupted; confirmation required before retry");
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Envío interrumpido; confirma el estado antes de reintentar", e);
    } catch (IOException e) {
      log.error("Batch upload not confirmed: errorType={}", e.getClass().getSimpleName());
      throw new IllegalStateException(
          "Envío no confirmado. Conserva el CSV y reintenta: el servidor deduplica por"
              + " identificador bancario",
          e);
    }
  }

  private HttpRequest buildRequest(String productId, MultipartBatchEncoder.EncodedBatch batch) {
    return HttpRequest.newBuilder(base.resolve("api/importer/products/" + productId + "/batches"))
        .timeout(Duration.ofSeconds(60))
        .header("X-Correlation-ID", correlationId())
        .header("Authorization", authorization)
        .header("Content-Type", "multipart/form-data; boundary=" + batch.boundary())
        .POST(batch.body())
        .build();
  }

  private String correlationId() {
    String id = org.slf4j.MDC.get("correlationId");
    return id == null ? UUID.randomUUID().toString() : id;
  }

  private Result decodeResponse(HttpResponse<String> response) throws IOException {
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      throw new IllegalStateException(
          "Importación rechazada por servidor (HTTP "
              + response.statusCode()
              + "). Revisa credencial, formato y snapshot; se conserva el CSV para reintentar.");
    return mapper.readValue(response.body(), Result.class);
  }
}
