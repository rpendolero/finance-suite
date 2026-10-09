package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.server.application.port.BankingAuthorizationPort;
import com.finance.server.application.port.BankingDataPort;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AccessDto;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AccountDetailsDto;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AuthorizationRequest;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AuthorizationResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.BalancesResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.BankDto;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.BanksResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.SessionRequest;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.SessionAuthorizationResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.SessionResponse;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.TransactionDto;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.TransactionsResponse;
import com.finance.server.infrastructure.config.EnableBankingProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EnableBankingAuthorizationAdapter implements BankingAuthorizationPort, BankingDataPort {
  private final String redirectUrl;
  private final EnableBankingApiClient client;
  private final EnableBankingDataMapper dataMapper = new EnableBankingDataMapper();

  public EnableBankingAuthorizationAdapter(EnableBankingProperties properties, ObjectMapper mapper) {
    URI base = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/");
    if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null)
      throw new IllegalArgumentException("Enable Banking API requires HTTPS");
    redirectUrl = properties.getRedirectUrl();
    EnableBankingJwtProvider jwt = EnableBankingJwtProvider.of(properties.getApplicationId(), properties.getPrivateKey(), mapper);
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    client = new EnableBankingApiClient(base, http, mapper, jwt::token);
  }

  EnableBankingAuthorizationAdapter(String redirectUrl, EnableBankingApiClient client) {
    this.redirectUrl = redirectUrl;
    this.client = client;
  }

  @Override
  public List<String> banks(String country) {
    List<BankDto> banks = aspsps(country).getAspsps();
    if (banks == null) throw new IllegalStateException("Enable Banking bank list is missing");
    return banks.stream().map(BankDto::getName).filter(Objects::nonNull)
        .filter(name -> !name.isBlank()).distinct().sorted().toList();
  }

  @Override
  public Authorization start(String bankName, String country, String state, OffsetDateTime validUntil) {
    AccessDto access = new AccessDto();
    access.setValidUntil(validUntil);
    BankDto bank = new BankDto();
    bank.setName(bankName);
    bank.setCountry(country);
    AuthorizationRequest request = AuthorizationRequest.builder().access(access).aspsp(bank)
        .state(state).redirectUrl(redirectUrl).psuType("personal").build();
    AuthorizationResponse response = client.post("auth", request, AuthorizationResponse.class);
    return new Authorization(dataMapper.required(response.getUrl(), "authorization URL"));
  }

  @Override
  public Session exchangeCode(String code) {
    SessionAuthorizationResponse response = client.post("sessions", SessionRequest.builder().code(code).build(), SessionAuthorizationResponse.class);
    return new Session(dataMapper.required(response.getSessionId(), "session id"));
  }

  public BanksResponse aspsps(String country) {
    return client.get("aspsps?country=" + encode(country), BanksResponse.class);
  }

  public SessionResponse session(String sessionId) {
    return client.get("sessions/" + encode(sessionId), SessionResponse.class);
  }

  public AccountDetailsDto account(String accountId) {
    return client.get("accounts/" + encode(accountId) + "/details", AccountDetailsDto.class);
  }

  public BalancesResponse balances(String accountId) {
    return client.get("accounts/" + encode(accountId) + "/balances", BalancesResponse.class);
  }

  @Override
  public List<Balance> balanceSnapshots(String accountId) {
    BalancesResponse response = balances(accountId);
    if (response.getBalances() == null) throw new IllegalStateException("Enable Banking balance list is missing");
    log.debug("Enable Banking balances received: count={}", response.getBalances().size());
    return response.getBalances().stream().map(dataMapper::balance).toList();
  }

  @Override
  public List<Account> accounts(String sessionId) {
    SessionResponse response = session(sessionId);
    List<String> ids = response.getAccounts();
    if (ids == null && response.getAccountsData() != null)
      ids = response.getAccountsData().stream().map(AccountDetailsDto::getUid).toList();
    if (ids == null) throw new IllegalStateException("Enable Banking session account list is missing");
    log.info("Enable Banking account discovery received: count={}", ids.size());
    return ids.stream().distinct().map(id -> {
      dataMapper.required(id, "account id");
      return dataMapper.account(id, account(id));
    }).toList();
  }

  @Override
  public List<Transaction> transactions(String accountId) {
    String path = "accounts/" + encode(accountId) + "/transactions";
    List<Transaction> result = new ArrayList<>();
    Set<String> seenKeys = new HashSet<>();
    String next = null;
    int pageNumber = 0;
    log.debug("Enable Banking transaction retrieval started");
    do {
      TransactionsResponse page = fetchTransactionPage(path, next);
      pageNumber++;
      log.debug("Enable Banking transaction page received: page={}, count={}, hasContinuation={}",
          pageNumber, page.getTransactions().size(), page.getContinuationKey() != null && !page.getContinuationKey().isBlank());
      mapTransactions(page.getTransactions(), result, pageNumber);
      next = normalizeContinuationKey(page.getContinuationKey());
      validateContinuationKey(next, seenKeys);
    } while (next != null);
    log.info("Enable Banking transaction retrieval completed: pages={}, count={}", pageNumber, result.size());
    return List.copyOf(result);
  }

  private TransactionsResponse fetchTransactionPage(String path, String continuationKey) {
    String url = path + (continuationKey == null ? "" : "?continuation_key=" + encode(continuationKey));
    TransactionsResponse page = client.get(url, TransactionsResponse.class);
    if (page.getTransactions() == null) throw new IllegalStateException("Enable Banking transaction list is missing");
    return page;
  }

  private void mapTransactions(List<TransactionDto> transactions, List<Transaction> result, int pageNumber) {
    for (var transaction : transactions) {
      try {
        result.add(dataMapper.transaction(transaction));
      } catch (RuntimeException e) {
        log.warn("Enable Banking transaction mapping failed: page={}, errorType={}", pageNumber, e.getClass().getSimpleName());
        throw e;
      }
    }
  }

  private String normalizeContinuationKey(String key) {
    if (key != null && key.isBlank()) return null;
    return key;
  }

  private void validateContinuationKey(String key, Set<String> seenKeys) {
    if (key != null && !seenKeys.add(key)) throw new IllegalStateException("Repeated Enable Banking continuation key");
  }

  private String encode(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Enable Banking identifier is required");
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
