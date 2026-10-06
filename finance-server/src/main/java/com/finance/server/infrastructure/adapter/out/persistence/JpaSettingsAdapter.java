package com.finance.server.infrastructure.adapter.out.persistence;

import com.finance.domain.*;
import com.finance.server.application.port.SettingsPort;
import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import jakarta.persistence.EntityManager;
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
  private final EntityManager entities;
  private final PersistenceMapper mapper;

  public List<ClassificationRule> rules() {
    return entities
        .createQuery(
            "select r from ClassificationRuleEntity r order by r.priority, r.id",
            ClassificationRuleEntity.class)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Transactional
  public void saveRule(ClassificationRule rule) {
    entities.merge(mapper.toEntity(rule));
    log.debug("Classification rule persisted using JPA");
  }

  @Transactional
  public void deleteRule(String id) {
    var rule = entities.find(ClassificationRuleEntity.class, id);
    if (rule != null) entities.remove(rule);
  }

  public List<Budget> budgets(String month) {
    return entities
        .createQuery(
            "select b from BudgetEntity b where b.month = :month order by b.category",
            BudgetEntity.class)
        .setParameter("month", month)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Transactional
  public void saveBudget(Budget budget) {
    entities.merge(mapper.toEntity(budget));
    log.debug("Budget persisted using JPA");
  }
}
