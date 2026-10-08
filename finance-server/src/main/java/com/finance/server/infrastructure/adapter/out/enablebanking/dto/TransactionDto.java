package com.finance.server.infrastructure.adapter.out.enablebanking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TransactionDto {
  private String transactionId;
  private String entryReference;
  private String id;
  private String bookingDate;
  private String valueDate;
  private String transactionDate;
  private AmountDto transactionAmount;
  private String creditDebitIndicator;
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<String> remittanceInformation;
  private String reference;
  private String additionalInformation;
  private String creditorName;
  private String debtorName;
  private PartyDto creditor;
  private PartyDto debtor;
  private String status;
}
