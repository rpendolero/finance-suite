package com.finance.statements;

import com.finance.domain.Product;
import com.finance.domain.Product.ProductType;
import com.finance.domain.Product.Provider;
import java.util.Set;

/** Supported, verified statement layouts. */
public enum StatementFormat {
  CSV("CSV normalizado", ".csv", null, Set.of(ProductType.values()), null),
  ING_ACCOUNT_XLS("ING · cuenta (Excel)", ".xls", Provider.ING, Set.of(ProductType.ACCOUNT), "ing-"),
  ING_CREDIT_CARD_XLS("ING · tarjeta de crédito (Excel)", ".xls", Provider.ING, Set.of(ProductType.CREDIT_CARD), "ing-card-"),
  KUTXABANK_ACCOUNT_XLS("Kutxabank · cuenta (Excel)", ".xls", Provider.KUTXABANK, Set.of(ProductType.ACCOUNT), "kutxabank-"),
  KUTXABANK_CARD_XLS("Kutxabank · tarjeta (Excel)", ".xls", Provider.KUTXABANK, Set.of(ProductType.CREDIT_CARD, ProductType.DEBIT_CARD), "kutxabank-card-");

  private final String label;
  private final String extension;
  private final Provider provider;
  private final Set<ProductType> types;
  private final String identifierPrefix;

  StatementFormat(String label, String extension, Provider provider, Set<ProductType> types, String identifierPrefix) {
    this.label = label;
    this.extension = extension;
    this.provider = provider;
    this.types = types;
    this.identifierPrefix = identifierPrefix;
  }

  public String label() { return label; }
  public String extension() { return extension; }
  public Provider provider() { return provider; }
  public String identifierPrefix() { return identifierPrefix; }
  public boolean supports(Product product) {
    return (provider == null || provider == product.provider()) && types.contains(product.type());
  }
}
