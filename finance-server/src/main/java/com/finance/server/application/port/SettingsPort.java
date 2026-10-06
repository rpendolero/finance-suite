package com.finance.server.application.port;

import com.finance.domain.*;
import java.util.List;

public interface SettingsPort {
  List<ClassificationRule> rules();

  void saveRule(ClassificationRule rule);

  void deleteRule(String id);

  List<Budget> budgets(String month);

  void saveBudget(Budget budget);
}
