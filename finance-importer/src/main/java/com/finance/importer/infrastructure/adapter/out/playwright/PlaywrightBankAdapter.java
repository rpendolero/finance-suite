package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.application.port.BankDownloadPort;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class PlaywrightBankAdapter implements BankDownloadPort {

    private final BrowserProperties config;
    private final List<BankExportFlow> flows;
    private final BankUrlPolicy urlPolicy;
    private final PrivateBrowserStorage storage;
    private final PlaywrightBrowserFactory browserFactory;

    @Override
    public synchronized Path download(Provider provider, String key) {

        log.info(
                "Browser download started: provider={}, exportKey={}",
                provider,
                key);

        var providerConfig = requireProvider(provider);
        var export = requireExport(providerConfig, key);
        var flow = selectFlow(provider, export);

        validateConfiguration(
                provider,
                providerConfig,
                export,
                flow);

        try {
            var directories =
                    storage.prepare(config, provider);

            return executeDownload(
                    provider,
                    providerConfig,
                    export,
                    flow,
                    directories);

        } catch (RuntimeException e) {

            log.error(
                    "Browser download failed: provider={}, exportKey={}, errorType={}",
                    provider,
                    key,
                    e.getClass().getSimpleName(),
                    e);

            throw e;
        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not prepare private browser storage",
                    e);
        }
    }

    private Path executeDownload(
            Provider provider,
            BrowserProperties.ProviderConfig providerConfig,
            BrowserProperties.Export export,
            BankExportFlow flow,
            PrivateBrowserStorage.Directories directories) {

        try (Playwright playwright = Playwright.create();
             BrowserContext context =
                     browserFactory.create(
                             playwright,
                             directories.profile())) {

            registerBrowserListeners(context);

            Page page = context.newPage();

            registerPageListeners(page);

      // IMPORTANTE: guardar la Page autenticada
      page = authenticate(
              page,
              provider,
              providerConfig,
              export,
              flow);

            log.info(
                    "Authentication completed; export navigation started: url={}",
                    page.url());

            var download =
                    flow.download(
                            page,
                            provider,
                            export);

            log.info(
                    "Download event received; private file save started");

            urlPolicy.validate(
                    provider,
                    page.url());

            Path saved =
                    storage.save(
                            download,
                            directories.downloads(),
                            flow.fileSuffix());

            log.info(
                    "Browser download saved to private storage");

            return saved;
        }
    }

    private BrowserProperties.ProviderConfig requireProvider(
            Provider provider) {

        if (!config.isEnabled()) {
            throw new IllegalStateException(
                    "Playwright is disabled; enable finance.browser.enabled");
        }

        var providerConfig =
                config.getProviders().get(provider);

        if (providerConfig == null) {
            throw new IllegalArgumentException(
                    "Provider not configured: " + provider);
        }

        return providerConfig;
    }

    private BrowserProperties.Export requireExport(
            BrowserProperties.ProviderConfig providerConfig,
            String key) {

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Export key is required");
        }

        var export =
                providerConfig.getExports().get(key);

        if (export == null) {
            throw new IllegalArgumentException(
                    "Export not configured: " + key);
        }

        return export;
    }

    private BankExportFlow selectFlow(
            Provider provider,
            BrowserProperties.Export export) {

        var matches =
                flows.stream()
                        .filter(flow -> flow.supports(provider, export))
                        .toList();

        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No export flow found for provider: " + provider);
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple export flows found for provider: " + provider);
        }

        return matches.getFirst();
    }

    private void validateConfiguration(
            Provider provider,
            BrowserProperties.ProviderConfig providerConfig,
            BrowserProperties.Export export,
            BankExportFlow flow) {

        flow.validate(export);

        urlPolicy.validate(
                provider,
                providerConfig.getLoginUrl());

        if (export.getUrl() != null) {
            urlPolicy.validate(
                    provider,
                    export.getUrl());
        }
    }

    private Page authenticate(
            Page page,
            Provider provider,
            BrowserProperties.ProviderConfig providerConfig,
            BrowserProperties.Export export,
            BankExportFlow flow) {

        page.setDefaultTimeout(
                config.getTimeoutMs());

        page.navigate(
                providerConfig.getLoginUrl());

        log.info(
                "Waiting for authentication and two-factor verification if required");


        var target = flow.authenticatedPage(page, export);
        log.info("Authentication target: {}", target);
        page = flow.authenticatedPage(page, export);

        urlPolicy.validate(
                provider,
                page.url());
        return page;
    }

    private void registerBrowserListeners(
            BrowserContext context) {

        context.onClose(
                ignored ->
                        log.debug("Browser context closed"));
    }

    private void registerPageListeners(
            Page page) {

        page.onCrash(
                ignored ->
                        log.error("Browser page crashed"));

        page.onClose(
                ignored ->
                        log.debug("Browser page closed"));
    }
}