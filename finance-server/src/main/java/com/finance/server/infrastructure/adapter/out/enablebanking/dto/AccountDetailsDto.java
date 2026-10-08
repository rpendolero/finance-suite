package com.finance.server.infrastructure.adapter.out.enablebanking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccountDetailsDto {
  @JsonAlias("id")
  private String uid;
  @JsonAlias({"product", "details"})
  private String name;
  private String currency;
  private AccountIdentificationDto accountId;
}
