package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PlaywrightBrowserFactory {

    private final BrowserProperties config;

    public BrowserContext create(
            Playwright playwright,
            Path profile) {

        var options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setChannel(config.getChannel())
                        .setAcceptDownloads(true);

        return playwright
                .chromium()
                .launchPersistentContext(
                        profile,
                        options);
    }
}