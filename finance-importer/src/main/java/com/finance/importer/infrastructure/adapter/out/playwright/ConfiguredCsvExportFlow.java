package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;

/** Existing generic CSV flow: requires independently verified configuration. */
public final class ConfiguredCsvExportFlow implements BankExportFlow {
  public boolean supports(Provider provider, BrowserProperties.Export export) {
    return export.getFlow() == null || export.getFlow().isBlank();
  }

  public void validate(BrowserProperties.Export export) {
    if (export.getUrl() == null
        || export.getAuthenticatedSelector() == null
        || export.getExportSelector() == null)
      throw new IllegalArgumentException("Exportación CSV no configurada");
  }

  public Locator authenticatedTarget(Page page, BrowserProperties.Export export) {
    return page.locator(export.getAuthenticatedSelector());
  }

  @Override
  public Page authenticatedPage(Page page, BrowserProperties.Export export) {
    authenticatedTarget(page, export).waitFor();
    return page;
  }

  public Download download(Page page, Provider provider, BrowserProperties.Export export) {
    page.navigate(export.getUrl());
    new BankUrlPolicy().validate(provider, page.url());
    return page.waitForDownload(() -> page.locator(export.getExportSelector()).click());
  }

  public String fileSuffix() {
    return ".csv";
  }
}
