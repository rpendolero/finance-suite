package com.finance.importer.infrastructure.adapter.out.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.domain.Product;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import lombok.RequiredArgsConstructor;

/** Encodes a batch independently of transport, authorization and HTTP response handling. */
@RequiredArgsConstructor
public final class MultipartBatchEncoder {
  private final ObjectMapper mapper;

  public record EncodedBatch(String boundary, HttpRequest.BodyPublisher body) {}

  public EncodedBatch encode(Path csv, Product snapshot) throws IOException {
    String boundary = "finance-" + UUID.randomUUID();
    var parts = new ArrayList<HttpRequest.BodyPublisher>();
    if (snapshot != null) appendSnapshot(parts, boundary, snapshot);
    appendStatement(parts, boundary, csv);
    parts.add(text("\r\n--" + boundary + "--\r\n"));
    return new EncodedBatch(
        boundary,
        HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new)));
  }

  private void appendSnapshot(
      List<HttpRequest.BodyPublisher> parts, String boundary, Product snapshot) throws IOException {
    parts.add(
        text(
            "--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"product\"\r\n"
                + "Content-Type: application/json\r\n\r\n"));
    parts.add(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(snapshot)));
    parts.add(text("\r\n"));
  }

  private void appendStatement(List<HttpRequest.BodyPublisher> parts, String boundary, Path csv)
      throws IOException {
    parts.add(
        text(
            "--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"movements.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"));
    parts.add(HttpRequest.BodyPublishers.ofFile(csv));
  }

  private HttpRequest.BodyPublisher text(String value) {
    return HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8);
  }
}
