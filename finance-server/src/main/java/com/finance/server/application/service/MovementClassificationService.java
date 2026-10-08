package com.finance.server.application.service;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Applies deterministic normalization and the highest-priority classification rule. */
@RequiredArgsConstructor
public final class MovementClassificationService {

  private final MerchantNormalizationService merchants;
  private final CategoryCatalogService categories;
  private final MovementKindDetectionService kindDetection;

  /** Backward-compatible constructor used outside the Spring configuration. */
  public MovementClassificationService() {
    this(
        new MerchantNormalizationService(),
        new CategoryCatalogService(),
        new MovementKindDetectionService());
  }

  public Movement classify(Movement movement, List<ClassificationRule> rules) {
    Movement normalized =
        Movement.normalizedCopy(
            movement, merchants.normalize(movement.merchant(), movement.description()));

    if (normalized.classificationSource() == Movement.ClassificationSource.MANUAL) {
      return normalized;
    }

    var matchedRule =
        rules.stream()
            .sorted(
                Comparator.comparingInt(ClassificationRule::priority)
                    .thenComparing(ClassificationRule::id))
            .filter(rule -> rule.matches(normalized))
            .filter(
                rule ->
                    !CategoryCatalogService.UNCLASSIFIED.equals(
                        categories.normalizeCategory(rule.category())))
            .findFirst();

    if (matchedRule.isPresent()) {
      return applyRule(normalized, matchedRule.orElseThrow());
    }

    String nonComputable = categories.nonComputableSubcategory(normalized.kind());
    if (nonComputable != null) {
      return normalized.withClassification(
          CategoryCatalogService.NON_COMPUTABLE, nonComputable, normalized.kind(),
          Movement.ClassificationSource.AUTOMATIC, new BigDecimal("0.9900"));
    }

    var detected = kindDetection.detect(normalized);
    if (detected.isPresent()) {
      var value = detected.orElseThrow();
      return normalized.withClassification(
          value.category(),
          value.subcategory(),
          value.kind(),
          Movement.ClassificationSource.AUTOMATIC,
          value.confidence());
    }

    String category = categories.normalizeCategory(normalized.category());
    if (!CategoryCatalogService.UNCLASSIFIED.equals(category)) {
      String subcategory = categories.normalizeSubcategory(category, normalized.subcategory());
      BigDecimal confidence =
          normalized.classificationConfidence() == null
                  || normalized.classificationConfidence().signum() == 0
              ? new BigDecimal("0.5000")
              : normalized.classificationConfidence();
      return normalized.withClassification(
          category,
          subcategory,
          normalized.kind(),
          Movement.ClassificationSource.AUTOMATIC,
          confidence);
    }

    return normalized.withClassification(
        CategoryCatalogService.UNCLASSIFIED,
        null,
        normalized.kind(),
        Movement.ClassificationSource.UNCLASSIFIED,
        BigDecimal.ZERO.setScale(4));
  }

  private Movement applyRule(Movement movement, ClassificationRule rule) {
    String excluded = categories.nonComputableSubcategory(rule.kind());
    String category = excluded == null ? categories.normalizeCategory(rule.category()) : CategoryCatalogService.NON_COMPUTABLE;
    String subcategory = excluded == null ? categories.normalizeSubcategory(category, rule.subcategory()) : excluded;
    Movement.ClassificationSource source =
        rule.matchType() == ClassificationRule.MatchType.MERCHANT
            ? Movement.ClassificationSource.MERCHANT_RULE
            : Movement.ClassificationSource.PATTERN_RULE;

    return movement.withClassification(
        category,
        subcategory,
        rule.kind(),
        source,
        rule.confidence());
  }
}
