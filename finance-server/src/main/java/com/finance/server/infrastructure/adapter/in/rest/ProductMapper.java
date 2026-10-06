package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {
  Product toDomain(ProductDto dto);

  ProductDto toDto(Product product);
}
