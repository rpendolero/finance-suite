package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public final class KutxabankCardExportFlow implements BankExportFlow {
    private final KutxabankPageSynchronizer synchronization = new KutxabankPageSynchronizer();
    private final KutxabankExportSupport exportSupport = new KutxabankExportSupport();

    public boolean supports(Provider provider, BrowserProperties.Export export) {
        return provider == Provider.KUTXABANK && "KUTXABANK_CARD".equals(export.getFlow());
    }

    public void validate(BrowserProperties.Export export) {
        new KutxabankExportSupport().validatePeriod(export);
    }

    public Locator authenticatedTarget(Page page, BrowserProperties.Export export) {
        return exportSupport.link(page, "Tarjetas");
    }

    @Override
    public Page authenticatedPage(Page page, BrowserProperties.Export export) {

        authenticatedTarget(page, export).waitFor();
        return page;
    }

    public Download download(Page page, Provider provider, BrowserProperties.Export export) {
        openMovements(page, export);
        exportSupport.searchPeriod(page, export);
        return new KutxabankExportSupport().download(page);
    }

    private void openMovements(Page page, BrowserProperties.Export export) {
        log.info("Kutxabank card navigation started");
        exportSupport.link(page, "Tarjetas").click();
        waiting();

        exportSupport.searchMovements(page, export.getCardName());
    }


    private void waiting() {
        try {
            log.info("Waiting");
            Thread.sleep(2000);
            log.info("Waiting finished");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String fileSuffix() {
        return ".xls";
    }

}
