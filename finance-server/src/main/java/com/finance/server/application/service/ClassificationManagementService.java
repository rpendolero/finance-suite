package com.finance.server.application.service;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import com.finance.domain.Period;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.SettingsPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class ClassificationManagementService {

  private final LedgerPort ledger;
  private final SettingsPort settings;
  private final MovementClassificationService classifier;
  private final CategoryCatalogService categories;
  private final MerchantNormalizationService merchants;

  public record ManualResult(Movement movement, int reclassified) {}

  public record ReclassificationResult(int scanned, int updated, int unclassified) {}

  public List<CategoryCatalogService.CategoryDefinition> categories() {
    return categories.categories();
  }

  public List<Movement> unclassified(Period period, String productId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 500));
    return ledger.movements(period, productId).stream()
        .filter(this::isUnclassified)
        .sorted(Comparator.comparing(Movement::date).reversed().thenComparing(Movement::id))
        .limit(safeLimit)
        .toList();
  }

  public ManualResult classifyManually(
      String id,
      String category,
      String subcategory,
      Movement.Kind kind,
      boolean createRule,
      boolean applyToSimilar) {

    String canonicalCategory = categories.normalizeCategory(category);
    boolean unclassified = CategoryCatalogService.UNCLASSIFIED.equals(canonicalCategory);
    String canonicalSubcategory =
        unclassified ? null : categories.normalizeSubcategory(canonicalCategory, subcategory);
    if (!unclassified) {
      categories.validate(canonicalCategory, subcategory);
    }
    if (unclassified && (createRule || applyToSimilar)) {
      throw new IllegalArgumentException("No se puede crear una regla para No categorizado");
    }

    kind = categories.classificationKind(canonicalCategory, canonicalSubcategory, kind);

    Movement current =
        ledger.movement(id)
            .orElseThrow(() -> new IllegalArgumentException("Movimiento inexistente: " + id));

    String normalizedMerchant = normalizedMerchant(current);

    Movement updated =
        Movement.normalizedCopy(current, normalizedMerchant)
            .withClassification(
                canonicalCategory,
                canonicalSubcategory,
                kind,
                unclassified
                    ? Movement.ClassificationSource.UNCLASSIFIED
                    : Movement.ClassificationSource.MANUAL,
                unclassified ? BigDecimal.ZERO.setScale(4) : BigDecimal.ONE.setScale(4));

    ClassificationRule merchantRule = null;
    if (createRule) {
      if (normalizedMerchant == null || normalizedMerchant.isBlank()) {
        throw new IllegalArgumentException(
            "No se puede crear una regla sin un comercio normalizado");
      }
      merchantRule = new ClassificationRule(
              manualRuleId(normalizedMerchant),
              10,
              ClassificationRule.MatchType.MERCHANT,
              normalizedMerchant,
              canonicalCategory,
              canonicalSubcategory,
              kind,
              new BigDecimal("0.9900"));
    }

    var changes = new ArrayList<Movement>();
    changes.add(updated);
    if (createRule && applyToSimilar) {
      for (Movement candidate : ledger.allMovements()) {
        if (id.equals(candidate.id()) || !normalizedMerchant.equals(normalizedMerchant(candidate))) continue;
        if (kind == Movement.Kind.REFUND && candidate.amount().signum() <= 0)
          throw new IllegalArgumentException("No se puede aplicar Devolución a cargos del comercio. Usa Solo este.");
        Movement.Kind candidateKind = kind == Movement.Kind.NORMAL && candidate.kind() == Movement.Kind.REFUND
            ? Movement.Kind.REFUND : kind;
        Movement classified = Movement.normalizedCopy(candidate, normalizedMerchant).withClassification(
            canonicalCategory, canonicalSubcategory, candidateKind, Movement.ClassificationSource.MANUAL, BigDecimal.ONE.setScale(4));
        if (!classified.equals(candidate)) changes.add(classified);
      }
    }
    // Validate all matching movements before persisting; never reclassify unrelated merchants here.
    ledger.updateClassifications(changes);
    if (merchantRule != null) settings.saveRule(merchantRule);
    int reclassified = createRule && applyToSimilar ? changes.size() : 0;

    log.info(
        "Movement manually classified: id={}, category={}, subcategory={}, ruleCreated={}, reclassified={}",
        id,
        canonicalCategory,
        canonicalSubcategory,
        createRule,
        reclassified);

    return new ManualResult(updated, reclassified);
  }

  public ClassificationRule saveRule(ClassificationRule rule) {
    String category = categories.normalizeCategory(rule.category());
    String subcategory = categories.normalizeSubcategory(category, rule.subcategory());
    categories.validate(category, rule.subcategory());

    ClassificationRule canonical =
        new ClassificationRule(
            rule.id(),
            rule.priority(),
            rule.matchType(),
            rule.contains(),
            category,
            subcategory,
            categories.classificationKind(category, subcategory, rule.kind()),
            rule.confidence());
    settings.saveRule(canonical);
    return canonical;
  }

  public ReclassificationResult reclassifyAll() {
    List<ClassificationRule> rules = settings.rules();
    List<Movement> current = ledger.allMovements();
    List<Movement> recalculated =
        current.stream()
            .map(movement -> classifier.classify(movement, rules))
            .toList();

    List<Movement> changed =
        java.util.stream.IntStream.range(0, current.size())
            .filter(i -> !current.get(i).equals(recalculated.get(i)))
            .mapToObj(recalculated::get)
            .toList();

    if (!changed.isEmpty()) ledger.updateClassifications(changed);

    int unclassified = (int) recalculated.stream().filter(this::isUnclassified).count();
    log.info(
        "Historical reclassification completed: scanned={}, updated={}, unclassified={}",
        current.size(),
        changed.size(),
        unclassified);
    return new ReclassificationResult(current.size(), changed.size(), unclassified);
  }

  private boolean isUnclassified(Movement movement) {
    return movement.classificationSource() == Movement.ClassificationSource.UNCLASSIFIED
        || CategoryCatalogService.UNCLASSIFIED.equalsIgnoreCase(movement.category());
  }

  private String normalizedMerchant(Movement movement) {
    return movement.normalizedMerchant() == null || movement.normalizedMerchant().isBlank()
        ? merchants.normalize(movement.merchant(), movement.description()) : movement.normalizedMerchant();
  }

  private String manualRuleId(String normalizedMerchant) {
    String slug =
        normalizedMerchant
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    if (slug.length() > 42) slug = slug.substring(0, 42);
    String hash = Integer.toUnsignedString(normalizedMerchant.hashCode(), 36);
    return "merchant_" + slug + "_" + hash;
  }
}
