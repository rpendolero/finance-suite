package com.finance.importer.application.service;

import com.finance.importer.application.port.FileCleanupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class ImportCleanupService {

    private final FileCleanupPort fileCleanupPort;

    public String cleanup(
            Path inputFile,
            Path source,
            Path prepared) {

        try {
            if (!prepared.equals(source)) {
                fileCleanupPort.deleteIfExists(prepared);
            }

            if (inputFile == null) {
                fileCleanupPort.deleteIfExists(source);
            }

            log.debug("Private statement cleanup completed");

            return null;

        } catch (RuntimeException e) {

            log.warn(
                    "Import confirmed, but private statement cleanup failed: errorType={}",
                    e.getClass().getSimpleName(),
                    e);

            return "Importado, pero no se pudo borrar la descarga privada";
        }
    }
}