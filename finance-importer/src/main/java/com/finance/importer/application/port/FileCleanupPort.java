package com.finance.importer.application.port;

import java.nio.file.Path;

public interface FileCleanupPort {

    void deleteIfExists(Path file);
}
