package com.finance.importer.application.port;

import java.nio.file.Path;

public interface StatementPreparationPort {
  void validate(Path csv);

  default Path prepare(Path input, com.finance.domain.Product.Provider provider) {
    validate(input);
    return input;
  }
}
