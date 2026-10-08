package com.finance.server.infrastructure.adapter.out.enablebanking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AuthorizationResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AuthorizationRequest;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class EnableBankingAdapterTest {
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final List<HttpRequest> requests = new ArrayList<>();

  private EnableBankingApiClient client(Function<HttpRequest, String> body, int status) throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
      HttpRequest request = invocation.getArgument(0);
      requests.add(request);
      HttpResponse<String> response = mock(HttpResponse.class);
      when(response.statusCode()).thenReturn(status);
      when(response.body()).thenReturn(body.apply(request));
      return response;
    });
    return new EnableBankingApiClient(URI.create("https://api.example.test/"), http, mapper, () -> "test-token");
  }

  @Test void postKeepsContentTypeAndGetWorksWithoutPrototypeHeaders() throws Exception {
    var api = client(request -> "{\"url\":\"https://bank.example/auth\",\"extra\":true}", 200);
    assertThat(api.post("auth", Map.of("code", "test"), AuthorizationResponse.class).getUrl()).startsWith("https:");
    api.get("auth", AuthorizationResponse.class);
    assertThat(requests.get(0).headers().firstValue("Content-Type")).contains("application/json");
    assertThat(requests.get(1).method()).isEqualTo("GET");
    assertThat(requests.get(1).headers().firstValue("Authorization")).contains("Bearer test-token");
  }

  @Test void sessionWithStringAccountIdsLoadsTypedAccountDetails() throws Exception {
    var api = client(request -> request.uri().getPath().startsWith("/sessions/")
        ? "{\"status\":\"AUTHORIZED\",\"accounts\":[\"first\",\"second\"],\"accounts_data\":[{\"uid\":\"first\"}]}"
        : "{\"currency\":\"EUR\",\"cash_account_type\":\"CARD\",\"account_id\":{\"iban\":\"ES00001234\"}}", 200);
    var adapter = new EnableBankingAuthorizationAdapter("https://app.example/callback", api);
    var accounts = adapter.accounts("session");
    assertThat(accounts).hasSize(2);
    assertThat(accounts.get(0).id()).isEqualTo("first");
    assertThat(accounts.get(0).cashAccountType()).isEqualTo("CARD");
    assertThat(accounts.get(0).name()).isEqualTo("Cuenta · 1234");
    assertThat(requests).hasSize(3);
    assertThat(requests.get(1).uri().getPath()).isEqualTo("/accounts/first/details");
    assertThat(requests.get(2).uri().getPath()).isEqualTo("/accounts/second/details");
  }

  @Test void emptyAuthorizedAccountListIsValidButMissingListIsRejected() throws Exception {
    var empty = new EnableBankingAuthorizationAdapter("/", client(request -> "{\"accounts\":[]}", 200));
    assertThat(empty.accounts("session")).isEmpty();
    var missing = new EnableBankingAuthorizationAdapter("/", client(request -> "{}", 200));
    assertThatThrownBy(() -> missing.accounts("session")).hasMessageContaining("account list is missing");
  }

  @Test void transactionsFollowPaginationAndConvertDebitAmountsAndRemittanceLists() throws Exception {
    var api = client(request -> request.uri().getQuery() == null
        ? "{\"transactions\":[],\"continuation_key\":\"next token\"}"
        : """
          {"transactions":[{"transaction_id":"tx","booking_date":"2026-10-08",
          "transaction_amount":{"amount":"12.34","currency":"EUR"},"credit_debit_indicator":"DBIT",
          "remittance_information":["Compra","supermercado"],"creditor":{"name":"Shop"},"status":"BOOK"}]}
          """, 200);
    var transactions = new EnableBankingAuthorizationAdapter("/", api).transactions("account");
    assertThat(transactions).hasSize(1);
    assertThat(transactions.get(0).amount()).isEqualByComparingTo("-12.34");
    assertThat(transactions.get(0).description()).isEqualTo("Compra supermercado");
    assertThat(transactions.get(0).merchant()).isEqualTo("Shop");
    assertThat(requests.get(1).uri().getRawQuery()).isEqualTo("continuation_key=next%20token");
  }

  @Test void repeatedPaginationKeyFailsInsteadOfLoopingForever() throws Exception {
    var adapter = new EnableBankingAuthorizationAdapter("/", client(request -> "{\"transactions\":[],\"continuation_key\":\"same\"}", 200));
    assertThatThrownBy(() -> adapter.transactions("account")).hasMessageContaining("Repeated");
    assertThat(requests).hasSize(2);
  }

  @Test void authorizationUsesTypedSnakeCaseRequest() throws Exception {
    var adapter = new EnableBankingAuthorizationAdapter("https://app.example/callback", client(request -> "{\"url\":\"https://bank.example/auth\"}", 200));
    assertThat(adapter.start("Bank", "ES", "state", OffsetDateTime.parse("2027-01-01T00:00:00Z")).url()).startsWith("https:");
    var request = AuthorizationRequest.builder()
        .redirectUrl("https://app.example/callback").psuType("personal").build();
    String json = mapper.writeValueAsString(request);
    assertThat(json).contains("\"redirect_url\"", "\"psu_type\"").doesNotContain("redirectUrl");
  }

  @Test void codeExchangeAcceptsAccountObjectsWithoutConfusingSessionLookupShape() throws Exception {
    var adapter = new EnableBankingAuthorizationAdapter("/", client(request ->
        "{\"session_id\":\"session\",\"accounts\":[{\"uid\":\"account\"}]}", 200));
    assertThat(adapter.exchangeCode("code").id()).isEqualTo("session");
  }

  @Test void providerErrorKeepsStatusAndSafeCodeWithoutResponseSecrets() throws Exception {
    var api = client(request -> "{\"error\":\"WRONG_REQUEST_PARAMETERS\",\"detail\":\"private-token\"}", 422);
    assertThatThrownBy(() -> api.get("auth", AuthorizationResponse.class))
        .hasMessageContaining("422").hasMessageContaining("WRONG_REQUEST_PARAMETERS")
        .hasMessageNotContaining("private-token");
  }
}
