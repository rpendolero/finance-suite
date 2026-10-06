package com.finance.importer.application.port;

import java.nio.file.Path;

public interface BankDownloadPort {
  Path download(com.finance.domain.Product.Provider provider, String exportKey);
}
