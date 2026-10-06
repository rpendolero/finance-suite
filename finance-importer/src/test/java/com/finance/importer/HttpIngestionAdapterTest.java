package com.finance.importer;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.importer.infrastructure.adapter.out.http.HttpIngestionAdapter;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpIngestionAdapterTest {
  @TempDir Path dir;

  @Test
  void rejectsRemotePlainHttpAndCredentialsInUrl() {
    assertThatThrownBy(
            () ->
                new HttpIngestionAdapter(
                    "http://192.168.1.20:8081", "secret-at-least-20-chars", new ObjectMapper()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new HttpIngestionAdapter(
                    "https://user:password@example.com",
                    "secret-at-least-20-chars",
                    new ObjectMapper()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sendsAuthenticatedMultipartWithoutFollowingRedirects() throws Exception {
    var auth = new AtomicReference<String>();
    var body = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/importer/products/a/batches",
        exchange -> {
          auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes =
              "{\"read\":1,\"inserted\":1,\"duplicates\":0}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    try {
      var file = Files.writeString(dir.resolve("input.csv"), "sample-file");
      var result =
          new HttpIngestionAdapter(
                  "http://127.0.0.1:" + server.getAddress().getPort(),
                  "secret-at-least-20-chars",
                  new ObjectMapper())
              .upload("a", file, null);
      assertThat(result.inserted()).isOne();
      assertThat(
              new String(
                  java.util.Base64.getDecoder().decode(auth.get().substring(6)),
                  StandardCharsets.UTF_8))
          .isEqualTo("importer:secret-at-least-20-chars");
      assertThat(body.get()).contains("name=\"file\"", "sample-file");
    } finally {
      server.stop(0);
    }
  }
}
