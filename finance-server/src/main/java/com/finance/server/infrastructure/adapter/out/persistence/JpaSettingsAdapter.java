package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.*;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import com.finance.server.infrastructure.adapter.out.persistence.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JpaSettingsAdapter implements SettingsPort {
  private final ClassificationRuleRepository rules;
  private final BudgetRepository budgets;
  private final PersistenceMapper mapper;

  public List<ClassificationRule> rules() {
    return rules.findAllByOrderByPriorityAscIdAsc().stream().map(mapper::toDomain).toList();
  }

  @Transactional
  public void saveRule(ClassificationRule rule) {
    rules.save(mapper.toEntity(rule));
    log.debug("Classification rule persisted using Spring Data JPA");
  }

  @Transactional
  public void deleteRule(String id) {
    rules.findById(id).ifPresent(rules::delete);
  }

  public List<Budget> budgets(String month) {
    return budgets.findByMonthOrderByCategoryAsc(month).stream().map(mapper::toDomain).toList();
  }

  @Transactional
  public void saveBudget(Budget budget) {
    budgets.save(mapper.toEntity(budget));
    log.debug("Budget persisted using Spring Data JPA");
  }
}
