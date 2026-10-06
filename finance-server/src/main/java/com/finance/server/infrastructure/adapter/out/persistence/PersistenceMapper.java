package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.*;
import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PersistenceMapper {
  ProductEntity toEntity(Product product);

  Product toDomain(ProductEntity entity);

  @Mapping(target = "bookingDate", source = "date")
  MovementEntity toEntity(Movement movement);

  @Mapping(target = "date", source = "bookingDate")
  Movement toDomain(MovementEntity entity);

  ClassificationRuleEntity toEntity(ClassificationRule rule);

  ClassificationRule toDomain(ClassificationRuleEntity entity);

  BudgetEntity toEntity(Budget budget);

  Budget toDomain(BudgetEntity entity);
}
