package com.finance.domain;

public record ClassificationRule(
    String id, int priority, String contains, String category, Movement.Kind kind) {
  public ClassificationRule {
    if (id == null
        || !id.matches("[a-zA-Z0-9_-]{1,64}")
        || contains == null
        || contains.isBlank()
        || contains.length() > 200
        || category == null
        || category.isBlank()
        || category.length() > 64
        || kind == null) throw new IllegalArgumentException("Regla inválida");
  }

  public boolean matches(Movement m) {
    var text =
        (m.description() + " " + (m.merchant() == null ? "" : m.merchant()))
            .toUpperCase(java.util.Locale.ROOT);
    return text.contains(contains.toUpperCase(java.util.Locale.ROOT));
  }
}
