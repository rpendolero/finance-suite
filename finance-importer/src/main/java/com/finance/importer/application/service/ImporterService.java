package com.finance.importer.application.service;

import com.finance.domain.Product;
import com.finance.importer.application.port.BankDownloadPort;
import com.finance.importer.application.port.IngestionPort;
import com.finance.importer.application.port.StatementPreparationPort;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class ImporterService {

    private static final String PRODUCT_ID_PATTERN = "[a-zA-Z0-9_-]{1,64}";

    private final BankDownloadPort bankDownloadPort;
    private final IngestionPort ingestionPort;
    private final StatementPreparationPort preparationPort;
    private final ImportCleanupService cleanupService;

    @Builder
    public record Job(String productId, Product.Provider provider, String exportKey, Path inputFile, Product snapshot) {

        public Job {
            validateProduct(productId, provider);
            validateSnapshot(productId, provider, snapshot);
            validateSource(inputFile, exportKey);
        }

        private static void validateProduct(String productId, Product.Provider provider) {

            if (productId == null || !productId.matches(PRODUCT_ID_PATTERN)) {
                throw new IllegalArgumentException("Invalid productId");
            }

            if (provider == null) {
                throw new IllegalArgumentException("Provider is required");
            }
        }

        private static void validateSnapshot(String productId, Product.Provider provider, Product snapshot) {

            if (snapshot == null) {
                return;
            }

            if (!snapshot.id().equals(productId) || snapshot.provider() != provider) {
                throw new IllegalArgumentException("Snapshot does not belong to product/provider");
            }
        }

        private static void validateSource(Path inputFile, String exportKey) {

            if (inputFile == null && (exportKey == null || exportKey.isBlank())) {
                throw new IllegalArgumentException("Either inputFile or exportKey is required");
            }
        }
    }

    public record Outcome(IngestionPort.Result result, String cleanupWarning) {
    }

    public Outcome execute(Job job) {

        long started = System.nanoTime();

        log.info("Import started: provider={}, source={}", job.provider(), getSourceType(job));

        try {
            Path source = obtainSource(job);

            log.info("Statement preparation started");
            Path prepared = preparationPort.prepare(source, job.provider());

            log.info("Statement preparation completed; upload started");
            IngestionPort.Result result = ingestionPort.upload(job.productId(), prepared, job.snapshot());
            String cleanupWarning = cleanupService.cleanup(job.inputFile(), source, prepared);

            log.info("Import completed: read={}, inserted={}, duplicates={}, durationMs={}", result.read(), result.inserted(), result.duplicates(), elapsedMillis(started));
            return new Outcome(result, cleanupWarning);

        } catch (RuntimeException e) {

            log.error("Import failed: errorType={}, durationMs={}", e.getClass().getSimpleName(), elapsedMillis(started), e);

            throw e;
        }
    }

    private Path obtainSource(Job job) {

        if (job.inputFile() != null) {
            return job.inputFile();
        }

        return bankDownloadPort.download(job.provider(), job.exportKey());
    }

    private String getSourceType(Job job) {
        return job.inputFile() == null ? "browser" : "local";
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}