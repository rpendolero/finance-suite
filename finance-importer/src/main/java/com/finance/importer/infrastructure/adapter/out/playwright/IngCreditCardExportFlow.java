package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;

@Slf4j
public final class IngCreditCardExportFlow extends IngExportFlow {
  @Override
  public boolean supports(Provider provider, BrowserProperties.Export export) {
    return provider == Provider.ING && "ING_CREDIT_CARD".equals(export.getFlow());
  }

  @Override
  public void validate(BrowserProperties.Export export) {
    if (export.getCardName() == null
        || export.getCardName().isBlank()
        || export.getFrom() == null
        || export.getTo() == null
        || export.getFrom().isAfter(export.getTo()))
      throw new IllegalArgumentException("Tarjeta ING y fechas from/to válidas requeridas");
  }

  @Override
  protected void selectElement(Page page, BrowserProperties.Export export) {
    page.waitForTimeout(2000);

    log.info("Waiting for ING card: {}", export.getAccountName());
    Locator accountLink = page.getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions()
                    .setName("Tarjeta Crédito  Terminada en 0096.")
    );
    accountLink.locator("xpath=..").click();
  }


}
