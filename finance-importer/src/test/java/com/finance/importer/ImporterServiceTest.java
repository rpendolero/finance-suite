package com.finance.importer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.domain.Product;
import com.finance.importer.application.port.*;
import com.finance.importer.application.service.ImporterService;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImporterServiceTest {
  @TempDir Path dir;

  @Test
  void sendsConvertedCsvAndKeepsManualOriginal() throws Exception {
    Path original = Files.writeString(dir.resolve("original.xls"), "source");
    Path converted = Files.writeString(dir.resolve("normalized.csv"), "normalized");
    var bank = mock(BankDownloadPort.class);
    var remote = mock(IngestionPort.class);
    var preparation = mock(StatementPreparationPort.class);
    when(preparation.prepare(original, Product.Provider.ING)).thenReturn(converted);
    when(remote.upload("a", converted, null)).thenReturn(new IngestionPort.Result(1, 1, 0));
    new ImporterService(bank, remote, preparation)
        .execute(new ImporterService.Job("a", Product.Provider.ING, null, original, null));
    assertThat(original).exists();
    assertThat(converted).doesNotExist();
    verify(remote).upload("a", converted, null);
  }

  @Test
  void removesDownloadOnlyAfterSuccessfulUpload() throws Exception {
    Path file = Files.writeString(dir.resolve("bank.csv"), "dummy");
    var bank = mock(BankDownloadPort.class);
    var remote = mock(IngestionPort.class);
    var preparation = mock(StatementPreparationPort.class, CALLS_REAL_METHODS);
    when(bank.download(Product.Provider.ING, "account")).thenReturn(file);
    when(remote.upload("a", file, null)).thenReturn(new IngestionPort.Result(1, 1, 0));
    new ImporterService(bank, remote, preparation)
        .execute(new ImporterService.Job("a", Product.Provider.ING, "account", null, null));
    assertThat(file).doesNotExist();
    verify(preparation).validate(file);
  }

  @Test
  void failedUploadRetainsDownloadForRetry() throws Exception {
    Path file = Files.writeString(dir.resolve("bank.csv"), "dummy");
    var bank = mock(BankDownloadPort.class);
    var remote = mock(IngestionPort.class);
    when(bank.download(Product.Provider.ING, "account")).thenReturn(file);
    when(remote.upload("a", file, null)).thenThrow(new IllegalStateException("network error"));
    assertThatThrownBy(
            () ->
                new ImporterService(
                        bank, remote, mock(StatementPreparationPort.class, CALLS_REAL_METHODS))
                    .execute(
                        new ImporterService.Job("a", Product.Provider.ING, "account", null, null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(file).exists();
  }

  @Test
  void manuallyProvidedCsvIsNeverDeleted() throws Exception {
    Path file = Files.writeString(dir.resolve("manual.csv"), "dummy");
    var bank = mock(BankDownloadPort.class);
    var remote = mock(IngestionPort.class);
    when(remote.upload("a", file, null)).thenReturn(new IngestionPort.Result(1, 1, 0));
    new ImporterService(bank, remote, mock(StatementPreparationPort.class, CALLS_REAL_METHODS))
        .execute(new ImporterService.Job("a", Product.Provider.PAYPAL, null, file, null));
    assertThat(file).exists();
    verifyNoInteractions(bank);
  }
}
