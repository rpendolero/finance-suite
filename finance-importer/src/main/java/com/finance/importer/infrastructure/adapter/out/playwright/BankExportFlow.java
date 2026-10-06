package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;

/** One navigation strategy per native export. */
public interface BankExportFlow {
  boolean supports(Provider provider, BrowserProperties.Export export);

  void validate(BrowserProperties.Export export);

  Locator authenticatedTarget(Page page, BrowserProperties.Export export);

  Page authenticatedPage(
          Page initialPage,
          BrowserProperties.Export export);

  Download download(Page page, Provider provider, BrowserProperties.Export export);

  String fileSuffix();
}
