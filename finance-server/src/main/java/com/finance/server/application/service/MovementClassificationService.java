package com.finance.server.application.service;

import com.finance.domain.*;
import java.util.List;

/** Applies the first matching rule only to movements that have no source category. */
public final class MovementClassificationService {
  public Movement classify(Movement movement, List<ClassificationRule> rules) {
    if (!"UNCLASSIFIED".equals(movement.category())) return movement;
    return rules.stream()
        .filter(rule -> rule.matches(movement))
        .findFirst()
        .map(rule -> applyRule(movement, rule))
        .orElse(movement);
  }

  private Movement applyRule(Movement movement, ClassificationRule rule) {
    return new Movement(
        movement.id(),
        movement.productId(),
        movement.externalId(),
        movement.date(),
        movement.amount(),
        movement.currency(),
        movement.description(),
        movement.merchant(),
        rule.category(),
        rule.kind(),
        movement.status());
  }
}
