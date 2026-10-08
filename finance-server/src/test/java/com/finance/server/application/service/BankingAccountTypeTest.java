package com.finance.server.application.service;

import com.finance.domain.Product;
import com.finance.server.application.port.BankConnectionPort;
import com.finance.server.application.port.BankingDataPort;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.domain.banking.BankConnection;
import com.finance.server.domain.banking.ExternalBankAccount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankingAccountTypeTest {
  private final Instant now = Instant.parse("2026-10-08T00:00:00Z");
  private final BankingDataPort banking = mock(BankingDataPort.class);
  private final BankConnectionPort connections = mock(BankConnectionPort.class);
  private final LedgerPort ledger = mock(LedgerPort.class);
  private final BankingSyncService service = new BankingSyncService(banking, connections, ledger,
      mock(SettingsPort.class), null, Clock.fixed(now, ZoneOffset.UTC));

  @Test void rediscoveryRefreshesTypeWithoutLosingProductOrId() {
    when(connections.find("connection")).thenReturn(Optional.of(new BankConnection("connection", "ENABLE_BANKING", "Bank", "ES", "session", null, now.plusSeconds(3600), BankConnection.Status.ACTIVE, null)));
    when(connections.accounts("connection")).thenReturn(List.of(account(null)));
    when(banking.accounts("session")).thenReturn(List.of(new BankingDataPort.Account("external", "Updated", "EUR", "CARD")));
    when(connections.saveAccount(any())).thenAnswer(call -> call.getArgument(0));
    var result = service.discoverAccounts("connection").get(0);
    assertThat(result.id()).isEqualTo("local");
    assertThat(result.productId()).isEqualTo("product");
    assertThat(result.name()).isEqualTo("Updated");
    assertThat(result.cashAccountType()).isEqualTo("CARD");
  }

  @Test void cardsAcceptBothCardProductsAndPreserveType() {
    for (var type : List.of(Product.ProductType.CREDIT_CARD, Product.ProductType.DEBIT_CARD)) {
      prepare("CARD", type);
      assertThat(service.link("connection", "external", "product").cashAccountType()).isEqualTo("CARD");
    }
  }

  @Test void cardsRejectAccountProducts() {
    prepare("CARD", Product.ProductType.ACCOUNT);
    assertThatThrownBy(() -> service.link("connection", "external", "product"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tarjeta");
  }

  @Test void currentAndSavingsAccountsRejectCardProducts() {
    for (String type : List.of("CACC", "SVGS")) {
      prepare(type, Product.ProductType.CREDIT_CARD);
      assertThatThrownBy(() -> service.link("connection", "external", "product"))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cuenta");
    }
  }

  @Test void oldAccountsWithoutTypeRemainLinkable() {
    prepare(null, Product.ProductType.ACCOUNT);
    assertThat(service.link("connection", "external", "product").productId()).isEqualTo("product");
  }

  private ExternalBankAccount account(String type) {
    return new ExternalBankAccount("local", "connection", "external", "product", "Account", "EUR", type);
  }
  private void prepare(String cashType, Product.ProductType productType) {
    when(connections.accounts("connection")).thenReturn(List.of(account(cashType)));
    when(ledger.product("product")).thenReturn(Optional.of(new Product("product", "Product", productType, "EUR", BigDecimal.ZERO, now, null, null)));
    when(connections.saveAccount(any())).thenAnswer(call -> call.getArgument(0));
  }
}
