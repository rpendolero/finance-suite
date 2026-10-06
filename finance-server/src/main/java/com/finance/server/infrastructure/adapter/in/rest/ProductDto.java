package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.Product.ProductType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class ProductDto {
  @NotNull
  private com.finance.domain.Product.Provider provider =
      com.finance.domain.Product.Provider.KUTXABANK;

  @NotBlank
  @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}")
  private String id;

  @NotBlank
  @Size(max = 100)
  private String name;

  @NotNull private ProductType type;

  @NotBlank
  @Pattern(regexp = "EUR")
  private String currency;

  @NotNull
  @Digits(integer = 16, fraction = 2)
  private BigDecimal balance;

  @NotNull private Instant balanceAt;

  @Size(max = 64)
  private String linkedAccountId;

  @PositiveOrZero
  @Digits(integer = 16, fraction = 2)
  private BigDecimal creditLimit;
}
