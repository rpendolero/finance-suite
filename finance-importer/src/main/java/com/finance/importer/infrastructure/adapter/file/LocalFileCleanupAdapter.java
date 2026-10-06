package com.finance.importer.infrastructure.adapter.file;

import com.finance.importer.application.port.FileCleanupPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileCleanupAdapter implements FileCleanupPort {

    @Override
    public void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}