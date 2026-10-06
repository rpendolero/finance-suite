package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;

@Slf4j
public abstract class IngExportFlow implements BankExportFlow {

    @Override
    public Page authenticatedPage(
            Page initialPage,
            BrowserProperties.Export export) {

        long timeout = 180_000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeout) {

            for (Page candidate : initialPage.context().pages()) {

                String url = candidate.url();

                log.info("ING candidate page: {}", url);

                if (url.startsWith("https://ing.ingdirect.es/pfm/")
                        && !url.contains("#login")) {

                    log.info(
                            "ING authenticated page detected: {}",
                            url);
                    candidate.waitForLoadState(LoadState.LOAD);
                    return candidate;
                }
            }

            initialPage.waitForTimeout(5000);
        }

        throw new IllegalStateException(
                "Timeout waiting for ING authentication");
    }

    @Override
    public Locator authenticatedTarget(
            Page page,
            BrowserProperties.Export export) {
        log.info("Current URL: {}", page.url());
        log.info("desktop-header count: {}",
                page.locator("desktop-header").count());

        log.info("Inicio count: {}",
                page.getByText("Inicio", new Page.GetByTextOptions()
                                .setExact(true))
                        .count());

        log.info("Inicio inside desktop-header count: {}",
                page.locator("desktop-header")
                        .getByText("Inicio",
                                new Locator.GetByTextOptions()
                                        .setExact(true))
                        .count());
        return page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions()
                        .setName(export.getAccountName()).setExact(true));
    }

    public Download download(Page page, Product.Provider provider, BrowserProperties.Export export) {
        log.info("Authenticated target: {}", page.url());
        selectElement(page, export);
        searchMovements(page, export);
        searchPeriod(page, export);
        return downloadExcel(page);
    }

    protected abstract void selectElement(Page page, BrowserProperties.Export export);

    private void searchMovements(Page page, BrowserProperties.Export export) {
        log.info("Waiting for search movements");
        page.waitForTimeout(2_000);

        Locator searchMovements = page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions()
                        .setName("Buscar movimientos")
                        .setExact(true)
        );

        searchMovements.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(30_000)
        );

        log.info("Clicking ING search movements link");
        searchMovements.click();

        Locator moreSearchOptions = page.getByText(
                "Más opciones de búsqueda",
                new Page.GetByTextOptions().setExact(true)
        );

        moreSearchOptions.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(30_000)
        );

        log.info("Clicking ING more search options");

        moreSearchOptions.click();
    }

    private void searchPeriod(Page page, BrowserProperties.Export export) {
        log.info("Waiting for search period");
        fillDates(page, export);
        Locator filters = page.getByLabel(
                "Filtros",
                new Page.GetByLabelOptions().setExact(true)
        );

        Locator searchButton = filters.getByRole(
                AriaRole.BUTTON,
                new Locator.GetByRoleOptions()
                        .setName("Buscar")
                        .setExact(true)
        );

        searchButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(30_000)
        );

        log.info(
                "ING filter search button found: count={}, visible={}",
                searchButton.count(),
                searchButton.isVisible()
        );

        searchButton.click();
    }

    private void fillDates(Page page, BrowserProperties.Export export) {
        log.info("Filling dates");
        var format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var from =
                page.getByRole(
                        AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Desde").setExact(true));
        from.fill(export.getFrom().format(format));
        from.press("Tab");
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Hasta").setExact(true))
                .fill(export.getTo().format(format));
    }

    private Download downloadExcel(Page page) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Descargar movimientos"))
                .click();
        return page.waitForDownload(
                () ->
                        page.getByText("Descargar Excel", new Page.GetByTextOptions().setExact(true)).click());
    }

    public String fileSuffix() {
        return ".xls";
    }
}
