package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.server.application.port.BankingAuthorizationPort;
import com.finance.server.application.port.BankingDataPort;
import com.finance.server.infrastructure.config.EnableBankingProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
public final class EnableBankingAuthorizationAdapter implements BankingAuthorizationPort, com.finance.server.application.port.BankingDataPort {
    private final URI base;
    private final String redirectUrl;
    private final ObjectMapper mapper;
    private final EnableBankingJwtProvider jwt;
    private final HttpClient http;

    public EnableBankingAuthorizationAdapter(EnableBankingProperties properties, ObjectMapper mapper) {
        if (!properties.getBaseUrl().startsWith("https://"))
            throw new IllegalArgumentException("Enable Banking API requires HTTPS");
        this.base = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/");
        this.redirectUrl = properties.getRedirectUrl();
        this.mapper = mapper;
        this.jwt = EnableBankingJwtProvider.of(properties.getApplicationId(), properties.getPrivateKey(), mapper);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<String> banks(String country) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode bank : aspsps(country).path("aspsps")) {
            String name = firstText(bank, "name");
            if (name != null) names.add(name);
        }
        return names.stream().distinct().sorted().toList();
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

    public JsonNode aspsps(String country) {
        return get("aspsps?country=" + URLEncoder.encode(country, StandardCharsets.UTF_8));
    }

    public JsonNode session(String sessionId) {
        return get("sessions/" + encodePath(sessionId));
    }

    public JsonNode account(String accountId) {
        return get("accounts/" + encodePath(accountId));
    }

    public JsonNode balances(String accountId) {
        return get("accounts/" + encodePath(accountId) + "/balances");
    }

    @Override
    public List<Account> accounts(String sessionId) {
        JsonNode root = session(sessionId);
        JsonNode items = root.path("accounts");
        if (!items.isArray()) return java.util.List.of();
        java.util.List<BankingDataPort.Account> result = new java.util.ArrayList<>();
        for (JsonNode item : items) {
            String id = firstText(item, "uid", "account_id", "id");
            String name = firstText(item, "name", "product", "details");
            String currency = firstText(item, "currency");
            if (id != null)
                result.add(new com.finance.server.application.port.BankingDataPort.Account(id, name == null ? id : name, currency == null ? "EUR" : currency));
        }
        return result;
    }

    @Override
    public List<BankingDataPort.Transaction> transactions(String accountId) {
        JsonNode root = get("accounts/" + encodePath(accountId) + "/transactions");
        JsonNode items = root.path("transactions");
        if (!items.isArray()) items = root;
        if (!items.isArray()) return java.util.List.of();
        java.util.List<BankingDataPort.Transaction> result = new java.util.ArrayList<>();
        for (JsonNode item : items) {
            String id = firstText(item, "transaction_id", "entry_reference", "id");
            String date = firstText(item, "booking_date", "value_date");
            JsonNode amountNode = item.path("transaction_amount");
            if (amountNode.isMissingNode()) amountNode = item.path("amount");
            String amount = amountNode.isObject() ? firstText(amountNode, "amount") : amountNode.asText(null);
            String currency = amountNode.isObject() ? firstText(amountNode, "currency") : firstText(item, "currency");
            String description = firstText(item, "remittance_information", "reference", "additional_information");
            String merchant = firstText(item, "creditor_name", "debtor_name");
            String status = firstText(item, "status");
            if (date != null && amount != null)
                result.add(new com.finance.server.application.port.BankingDataPort.Transaction(id, java.time.LocalDate.parse(date.substring(0, 10)), new java.math.BigDecimal(amount), currency == null ? "EUR" : currency, description == null ? "" : description, merchant, "PDNG".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)));
        }
        return result;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

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
        var builder = HttpRequest.newBuilder(prototype.uri())
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + jwt.token());

        prototype.headers().map().forEach((name, values) ->
                values.forEach(value -> builder.header(name, value)));

        HttpRequest request = builder
                .method(
                        prototype.method(),
                        prototype.bodyPublisher()
                                .orElse(HttpRequest.BodyPublishers.noBody()))
                .build();
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
        if (value == null || value.isBlank())
            throw new IllegalStateException("Enable Banking response is missing " + field);
        return value;
    }

    private String encodePath(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Enable Banking id is required");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
