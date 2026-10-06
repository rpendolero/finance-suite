package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface BudgetRepository extends JpaRepository<BudgetEntity, BudgetId> {
  List<BudgetEntity> findByMonthOrderByCategoryAsc(String month);
}
