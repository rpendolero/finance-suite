package com.finance.importer.infrastructure.config;

import com.finance.domain.Product.Provider;
import java.util.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("finance.browser")
public class BrowserProperties {
  private boolean enabled = false;
  private String profileDir = "./private/browser-profiles";
  private String downloadDir = "./private/downloads";
  private double timeoutMs = 180000;
  private String channel = "chrome";
  private Map<Provider, ProviderConfig> providers = defaults();

  private static Map<Provider, ProviderConfig> defaults() {
    var map = new EnumMap<Provider, ProviderConfig>(Provider.class);
    map.put(Provider.KUTXABANK, new ProviderConfig("https://clientes.kutxabank.es/"));
    map.put(Provider.ING, new ProviderConfig("https://www.ing.es/"));
    map.put(Provider.PAYPAL, new ProviderConfig("https://www.paypal.com/signin"));
    return map;
  }

  @Data
  public static class ProviderConfig {
    private String loginUrl;
    private Map<String, Export> exports = new HashMap<>();

    public ProviderConfig() {}

    public ProviderConfig(String loginUrl) {
      this.loginUrl = loginUrl;
    }
  }

  @Data
  public static class Export {
    private String flow;
    private String accountName = "Cuenta NÓMINA Terminada en";
    private String cardName = "Tarjeta Crédito Terminada en";
    private java.time.LocalDate from;
    private java.time.LocalDate to;
    private String url;
    private String authenticatedSelector;
    private String exportSelector;
  }
}
