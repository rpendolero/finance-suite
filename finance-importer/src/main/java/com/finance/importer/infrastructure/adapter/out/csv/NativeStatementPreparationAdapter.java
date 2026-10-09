package com.finance.importer.infrastructure.adapter.out.csv;

import com.finance.domain.Product.Provider;
import com.finance.importer.application.port.StatementPreparationPort;
import com.finance.statements.NativeXlsStatementPreparationAdapter;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class NativeStatementPreparationAdapter implements StatementPreparationPort {
  private final NativeXlsStatementPreparationAdapter preparation;

  @Override
  public void validate(Path input) {
    preparation.validate(input);
  }

  @Override
  public Path prepare(Path input, Provider provider) {
    return preparation.prepare(input, provider);
  }
}
