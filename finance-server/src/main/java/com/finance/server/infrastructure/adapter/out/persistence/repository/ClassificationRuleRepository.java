package com.finance.server.infrastructure.adapter.out.persistence.repository;

import com.finance.server.infrastructure.adapter.out.persistence.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ClassificationRuleRepository extends JpaRepository<ClassificationRuleEntity, String> {
  List<ClassificationRuleEntity> findAllByOrderByPriorityAscIdAsc();
}
