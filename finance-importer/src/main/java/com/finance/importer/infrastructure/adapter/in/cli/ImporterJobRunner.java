package com.finance.importer.infrastructure.adapter.in.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.domain.Product;
import com.finance.importer.application.service.ImporterService;
import com.finance.importer.infrastructure.config.ImporterProperties;
import java.io.IOException;
import java.nio.file.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.*;

/**
 * Console input/output belongs to an inbound adapter, not configuration or application services.
 */
@Slf4j
@RequiredArgsConstructor
public final class ImporterJobRunner implements ApplicationRunner {
  private final ImporterProperties config;
  private final ImporterService importer;
  private final ObjectMapper mapper;

  public void run(ApplicationArguments args) throws IOException {
    var job = selectedJob();
    var result = importer.execute(buildJob(job));
    printOutcome(result);
  }

  private ImporterProperties.JobConfig selectedJob() {
    if (config.getJob() == null || !config.getJobs().containsKey(config.getJob()))
      throw new IllegalArgumentException("Selecciona un trabajo con --finance.importer.job=nombre");
    return config.getJobs().get(config.getJob());
  }

  private ImporterService.Job buildJob(ImporterProperties.JobConfig job) throws IOException {
    return new ImporterService.Job(
        job.getProductId(),
        job.getProvider(),
        job.getExportKey(),
        job.getInputFile() == null ? null : Path.of(job.getInputFile()),
        readSnapshot(job));
  }

  private Product readSnapshot(ImporterProperties.JobConfig job) throws IOException {
    return job.getProductFile() == null
        ? null
        : mapper.readValue(Files.readAllBytes(Path.of(job.getProductFile())), Product.class);
  }

  private void printOutcome(ImporterService.Outcome outcome) {
    log.info(
        "Job finished: read={}, inserted={}, duplicates={}",
        outcome.result().read(),
        outcome.result().inserted(),
        outcome.result().duplicates());
    if (outcome.cleanupWarning() != null) log.warn("Private statement cleanup requires attention");
  }
}
