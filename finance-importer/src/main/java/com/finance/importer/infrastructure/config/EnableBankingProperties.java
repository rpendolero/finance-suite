package com.finance.importer.infrastructure.config;

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
  private String redirectUrl = "http://127.0.0.1:8082/enable-banking/callback";
  private String country = "ES";
}
