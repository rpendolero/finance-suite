package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.Download;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

public final class PrivateBrowserStorage {
  public record Directories(Path profile, Path downloads) {}

  public Directories prepare(BrowserProperties properties, Provider provider) throws IOException {
    Path profile =
        Path.of(properties.getProfileDir())
            .resolve(provider.name().toLowerCase(Locale.ROOT))
            .toAbsolutePath();
    Path downloads = Path.of(properties.getDownloadDir()).toAbsolutePath();
    createPrivateDirectory(profile);
    createPrivateDirectory(downloads);
    return new Directories(profile, downloads);
  }

  private void createPrivateDirectory(Path path) throws IOException {
    Files.createDirectories(path);
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
  }

  public Path save(Download download, Path directory, String suffix)  {
    if (!download.suggestedFilename().toLowerCase(Locale.ROOT).endsWith(suffix))
      throw new IllegalStateException("Formato de descarga inesperado; se esperaba " + suffix);
    Path target = directory.resolve(UUID.randomUUID() + suffix);
    download.saveAs(target);
      try {
          Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
      return target;
  }
}
