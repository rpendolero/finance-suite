package com.finance.server.infrastructure.adapter.out.enablebanking;

import com.finance.server.application.port.BankingDataPort.Account;
import com.finance.server.application.port.BankingDataPort.Balance;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.BalanceDto;
import com.finance.server.application.port.BankingDataPort.Transaction;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.AccountDetailsDto;
import com.finance.server.infrastructure.adapter.out.enablebanking.dto.TransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class EnableBankingDataMapper {
  public Account account(String id, AccountDetailsDto details) {
    String name = first(details.getName());
    if (name == null && details.getAccountId() != null) {
      String iban = details.getAccountId().getIban();
      if (iban != null && iban.length() >= 4) name = "Cuenta · " + iban.substring(iban.length() - 4);
    }
    return new Account(id, name == null ? id : name, required(details.getCurrency(), "account currency"), details.getCashAccountType());
  }

  public Balance balance(BalanceDto value) {
    if (value.getBalanceAmount() == null || value.getBalanceAmount().getAmount() == null)
      throw new IllegalStateException("Enable Banking balance amount is missing");
    BigDecimal amount = value.getBalanceAmount().getAmount();
    if ("DBIT".equalsIgnoreCase(value.getCreditDebitIndicator())) amount = amount.abs().negate();
    if ("CRDT".equalsIgnoreCase(value.getCreditDebitIndicator())) amount = amount.abs();
    var at = value.getLastChangeDateTime() != null ? OffsetDateTime.parse(value.getLastChangeDateTime()).toInstant()
        : value.getReferenceDate() != null ? LocalDate.parse(value.getReferenceDate()).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
    return new Balance(amount, required(value.getBalanceAmount().getCurrency(), "balance currency"), value.getBalanceType(), at);
  }

  public Transaction transaction(TransactionDto value) {
    if (value.getTransactionAmount() == null || value.getTransactionAmount().getAmount() == null)
      throw new IllegalStateException("Enable Banking transaction amount is missing");
    BigDecimal amount = value.getTransactionAmount().getAmount();
    if ("DBIT".equalsIgnoreCase(value.getCreditDebitIndicator())) amount = amount.abs().negate();
    if ("CRDT".equalsIgnoreCase(value.getCreditDebitIndicator())) amount = amount.abs();
    String date = required(first(value.getBookingDate(), value.getValueDate(), value.getTransactionDate()), "transaction date");
    String description = join(value.getRemittanceInformation());
    if (description.isBlank()) description = first(value.getReference(), value.getAdditionalInformation());
    String creditor = value.getCreditor() == null ? null : value.getCreditor().getName();
    String debtor = value.getDebtor() == null ? null : value.getDebtor().getName();
    String merchant = amount.signum() < 0 ? first(value.getCreditorName(), creditor, value.getDebtorName(), debtor)
        : first(value.getDebtorName(), debtor, value.getCreditorName(), creditor);
    return new Transaction(first(value.getTransactionId(), value.getEntryReference(), value.getId()),
        LocalDate.parse(date.substring(0, 10)), amount,
        required(value.getTransactionAmount().getCurrency(), "transaction currency"),
        description == null ? "" : description, merchant,
        "PDNG".equalsIgnoreCase(value.getStatus()) || "PENDING".equalsIgnoreCase(value.getStatus()));
  }

  private String join(List<String> values) {
    return values == null ? "" : String.join(" ", values.stream().filter(Objects::nonNull).toList());
  }

  private String first(String... values) {
    return Stream.of(values).filter(Objects::nonNull).filter(v -> !v.isBlank()).findFirst().orElse(null);
  }

  public String required(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalStateException("Enable Banking response is missing " + field);
    return value;
  }
}
