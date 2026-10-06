package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;

@Slf4j
public final class IngAccountExportFlow extends IngExportFlow {

  @Override
  public boolean supports(Provider provider, BrowserProperties.Export export) {
    return provider == Provider.ING && "ING_ACCOUNT".equals(export.getFlow());
  }

  @Override
  public void validate(BrowserProperties.Export export) {
    if (export.getAccountName() == null || export.getAccountName().isBlank())
      throw new IllegalArgumentException("Cuenta ING requerida");
  }


  @Override
  protected void selectElement(Page page, BrowserProperties.Export export) {

    page.waitForTimeout(2000);

    log.info("Waiting for ING account: {}", export.getAccountName());
    Locator accountLink = page.getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions()
                    .setName("Cuenta NÓMINA  Terminada en 8258.")
    );
    accountLink.locator("xpath=..").click();
  }
}
