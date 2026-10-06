package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import java.net.URI;
import java.util.List;

public final class BankUrlPolicy {
  public void validate(Provider provider, String url) {
    var uri = URI.create(url);
    var host = uri.getHost();
    var domains =
        switch (provider) {
          case KUTXABANK -> List.of("kutxabank.es");
          case ING -> List.of("ing.es", "ingdirect.es");
          case PAYPAL -> List.of("paypal.com");
        };
    if (!"https".equals(uri.getScheme())
        || host == null
        || uri.getUserInfo() != null
        || domains.stream().noneMatch(d -> host.equals(d) || host.endsWith("." + d)))
      throw new IllegalArgumentException(
          "URL fuera de los dominios HTTPS del proveedor " + provider);
  }
}
