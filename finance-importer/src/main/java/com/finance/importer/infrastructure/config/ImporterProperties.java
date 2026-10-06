package com.finance.importer.infrastructure.config;

import com.finance.domain.Product;
import java.util.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("finance.importer")
public class ImporterProperties {
  private String serverUrl = "http://127.0.0.1:8081";
  @ToString.Exclude private String password;
  private String job;
  @ToString.Exclude private Map<String, JobConfig> jobs = new LinkedHashMap<>();

  @Data
  public static class JobConfig {
    private String productId;
    private Product.Provider provider;
    private String exportKey;
    private String inputFile;
    private String productFile;
  }
}
