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
public final class KutxabankAccountExportFlow implements BankExportFlow {
    private final KutxabankPageSynchronizer synchronization = new KutxabankPageSynchronizer();
    private final KutxabankExportSupport exportSupport = new KutxabankExportSupport();

    public boolean supports(Provider provider, BrowserProperties.Export export) {
        return provider == Provider.KUTXABANK && "KUTXABANK_ACCOUNT".equals(export.getFlow());
    }

    public void validate(BrowserProperties.Export export) {
        if (export.getAccountName() == null
                || export.getAccountName().isBlank()
                || "Cuenta NÓMINA Terminada en".equals(export.getAccountName())
                || export.getFrom() == null
                || export.getTo() == null
                || export.getFrom().isAfter(export.getTo()))
            throw new IllegalArgumentException("Cuenta Kutxabank y fechas from/to válidas requeridas");
    }

    @Override
    public Locator authenticatedTarget(Page page, BrowserProperties.Export export) {
        return exportSupport.link(page, "Cuentas");
    }

    @Override
    public Page authenticatedPage(Page page, BrowserProperties.Export export) {

        authenticatedTarget(page, export).waitFor();
        return page;
    }

    public Download download(Page page, Provider provider, BrowserProperties.Export export) {
        openMovements(page, export);
        searchPeriod(page, export);
        return new KutxabankExportSupport().download(page);
    }

    private void openMovements(Page page, BrowserProperties.Export export) {
        log.info("Kutxabank account navigation started");
        //synchronization.execute(page, "open-accounts", () -> link(page, "Cuentas").click());
        exportSupport.link(page, "Cuentas").click();
        waiting();

        //synchronization.execute(page, "open-account-queries", () -> link(page, "Consultas").click());
        exportSupport.searchMovements(page, export.getAccountName());
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

    private void searchPeriod(Page page, BrowserProperties.Export export) {
        synchronization.awaitReady(page);
        page.getByRole(
                        AriaRole.RADIO, new Page.GetByRoleOptions().setName("Entre fechas").setExact(true))
                .check();
        exportSupport.search(page, export);
    }

    public String fileSuffix() {
        return ".xls";
    }
}
