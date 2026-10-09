package com.finance.server.infrastructure.adapter.out.enablebanking.dto;

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
public class SessionResponse {
  private String sessionId;
  private String status;
  private List<String> accounts;
  private List<AccountDetailsDto> accountsData;
  private AccessDto access;
}
