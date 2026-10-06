package com.finance.importer.application.port;

import com.finance.domain.Product;
import java.nio.file.Path;

public interface IngestionPort {
  record Result(int read, int inserted, int duplicates) {}

  Result upload(String productId, Path csv, Product snapshot);
}
