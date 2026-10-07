package com.finance.importer.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.importer.application.port.*;
import com.finance.importer.application.service.ImportCleanupService;
import com.finance.importer.application.service.ImporterService;
import com.finance.importer.infrastructure.adapter.file.LocalFileCleanupAdapter;
import com.finance.importer.infrastructure.adapter.in.cli.ImporterJobRunner;
import com.finance.importer.infrastructure.adapter.out.csv.*;
import com.finance.importer.infrastructure.adapter.out.http.HttpIngestionAdapter;
import com.finance.importer.infrastructure.adapter.out.enablebanking.EnableBankingClient;
import com.finance.importer.infrastructure.adapter.out.playwright.*;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ImporterProperties.class,
        BrowserProperties.class,
        EnableBankingProperties.class
})
public class ImporterConfig {

  // -------------------------------------------------------------------------
  // Playwright flows
  // -------------------------------------------------------------------------

  @Bean
  BankExportFlow ingAccountExportFlow() {
    return new IngAccountExportFlow();
  }

  @Bean
  BankExportFlow ingCreditCardExportFlow() {
    return new IngCreditCardExportFlow();
  }

  @Bean
  BankExportFlow kutxabankAccountExportFlow() {
    return new KutxabankAccountExportFlow();
  }

  @Bean
  BankExportFlow kutxabankCardExportFlow() {
    return new KutxabankCardExportFlow();
  }

  @Bean
  BankExportFlow configuredCsvExportFlow() {
    return new ConfiguredCsvExportFlow();
  }

  // -------------------------------------------------------------------------
  // Playwright infrastructure
  // -------------------------------------------------------------------------

  @Bean
  BankUrlPolicy bankUrlPolicy() {
    return new BankUrlPolicy();
  }

  @Bean
  PrivateBrowserStorage privateBrowserStorage() {
    return new PrivateBrowserStorage();
  }

  @Bean
  PlaywrightBrowserFactory playwrightBrowserFactory(
          BrowserProperties properties) {

    return new PlaywrightBrowserFactory(properties);
  }

  @Bean
  BankDownloadPort browser(
          BrowserProperties properties,
          List<BankExportFlow> flows,
          BankUrlPolicy urlPolicy,
          PrivateBrowserStorage storage,
          PlaywrightBrowserFactory browserFactory) {

    return new PlaywrightBankAdapter(
            properties,
            flows,
            urlPolicy,
            storage,
            browserFactory);
  }

  @Bean
  @ConditionalOnProperty(prefix = "finance.enable-banking", name = "enabled", havingValue = "true")
  EnableBankingClient enableBankingClient(EnableBankingProperties properties, ObjectMapper mapper) {
    return new EnableBankingClient(properties, mapper);
  }

  // -------------------------------------------------------------------------
  // Statement preparation
  // -------------------------------------------------------------------------

  @Bean
  StatementPreparationPort csvPreparation() {

    return new NativeXlsStatementPreparationAdapter(
            List.of(
                    new IngAccountStatementFormat(),
                    new IngCreditCardStatementFormat(),
                    new KutxabankAccountStatementFormat(),
                    new KutxabankCardStatementFormat()),
            new CanonicalCsvValidator());
  }

  // -------------------------------------------------------------------------
  // Server ingestion
  // -------------------------------------------------------------------------

  @Bean
  IngestionPort ingestion(
          ImporterProperties properties,
          ObjectMapper mapper) {

    return new HttpIngestionAdapter(
            properties.getServerUrl(),
            properties.getPassword(),
            mapper);
  }

  // -------------------------------------------------------------------------
  // File cleanup
  // -------------------------------------------------------------------------

  @Bean
  FileCleanupPort fileCleanupPort() {
    return new LocalFileCleanupAdapter();
  }

  @Bean
  ImportCleanupService importCleanupService(
          FileCleanupPort fileCleanupPort) {

    return new ImportCleanupService(fileCleanupPort);
  }

  // -------------------------------------------------------------------------
  // Application service
  // -------------------------------------------------------------------------

  @Bean
  ImporterService importer(
          BankDownloadPort browser,
          IngestionPort server,
          StatementPreparationPort preparation,
          ImportCleanupService cleanupService) {

    return new ImporterService(
            browser,
            server,
            preparation,
            cleanupService);
  }

  // -------------------------------------------------------------------------
  // CLI
  // -------------------------------------------------------------------------

  @Bean
  ApplicationRunner run(
          ImporterProperties config,
          ImporterService importer,
          ObjectMapper mapper) {

    return new ImporterJobRunner(
            config,
            importer,
            mapper);
  }
}