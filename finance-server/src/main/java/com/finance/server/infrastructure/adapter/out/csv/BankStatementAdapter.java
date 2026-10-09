package com.finance.server.infrastructure.adapter.out.csv;

import com.finance.domain.Movement;
import com.finance.server.application.port.StatementParserPort;
import com.finance.statements.NativeXlsStatementPreparationAdapter;
import com.finance.statements.StatementFormat;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Reuses the same bank layouts as the automated importer. */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public final class BankStatementAdapter implements StatementParserPort {
  private static final long MAX_BYTES = 10L * 1024 * 1024;
  private final CsvStatementAdapter csv;
  private final NativeXlsStatementPreparationAdapter nativeStatements = new NativeXlsStatementPreparationAdapter();

  @Override
  public List<Movement> parse(InputStream input, String productId) {
    return csv.parse(input, productId);
  }

  @Override
  public List<Movement> parse(InputStream input, String productId, StatementFormat format) {
    if (format == StatementFormat.CSV) return parse(input, productId);
    Path original = null;
    Path normalized = null;
    log.info("Bank statement parsing started: productId={}, format={}", productId, format);
    try {
      original = Files.createTempFile("bank-statement-", ".xls");
      // Bound disk usage even when called outside the multipart controller.
      try (var output = Files.newOutputStream(original)) {
        long bytes = input.transferTo(new LimitedOutputStream(output, MAX_BYTES));
        if (bytes == 0) throw new IllegalArgumentException("El fichero está vacío");
      }
      normalized = nativeStatements.prepare(original, format);
      try (var converted = Files.newInputStream(normalized)) {
        return csv.parse(converted, productId);
      }
    } catch (IOException failure) {
      throw new IllegalArgumentException("No se pudo leer el extracto bancario", failure);
    } finally {
      remove(normalized);
      remove(original);
    }
  }

  private void remove(Path path) {
    if (path == null) return;
    try {
      Files.deleteIfExists(path);
    } catch (IOException failure) {
      log.warn("Temporary statement cleanup failed: errorType={}", failure.getClass().getSimpleName());
    }
  }

  private static final class LimitedOutputStream extends FilterOutputStream {
    private final long limit;
    private long written;

    LimitedOutputStream(OutputStream output, long limit) {
      super(output);
      this.limit = limit;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      if (written + length > limit) throw new IllegalArgumentException("Máximo 10 MB por fichero");
      out.write(bytes, offset, length);
      written += length;
    }

    @Override
    public void write(int value) throws IOException {
      if (++written > limit) throw new IllegalArgumentException("Máximo 10 MB por fichero");
      out.write(value);
    }
  }
}
