package com.finance.server.infrastructure.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("finance.enable-banking")
public class EnableBankingProperties {
  private boolean enabled = false;
  private String baseUrl = "https://api.enablebanking.com";
  private String applicationId;
  @ToString.Exclude private String privateKey;
  private String redirectUrl = "https://finances.myaihome.es/api/v1/banking/enable-banking/callback";
  private String frontendUrl = "/";
  private String country = "ES";
  private int consentDays = 90;
}
