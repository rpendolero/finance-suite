package com.finance.server.infrastructure.adapter.in.mapper;

import com.finance.domain.Product;
import com.finance.server.infrastructure.adapter.in.dto.ProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {
  Product toDomain(ProductDto dto);

  ProductDto toDto(Product product);
}
