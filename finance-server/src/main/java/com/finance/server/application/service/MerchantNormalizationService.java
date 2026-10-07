package com.finance.server.application.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Produces a stable merchant name from noisy bank descriptions without discarding the original text. */
public final class MerchantNormalizationService {

  private static final Map<String, String> KNOWN = knownMerchants();

  public String normalize(String merchant, String description) {
    String source = chooseSource(merchant, description);
    if (source == null || source.isBlank()) return null;

    String normalized = ascii(source)
        .replaceAll("^COMPRA\\s+(EN\\s+)?", "")
        .replaceAll("^PAGO\\s+(EN\\s+)?", "")
        .replaceAll("^PAYPAL\\s*[*#:-]?\\s*", "")
        .replaceAll("[*#][A-Z0-9_-]{4,}$", "")
        .replaceAll("\\s+", " ")
        .trim();

    for (var entry : KNOWN.entrySet()) {
      if (normalized.contains(entry.getKey())) return entry.getValue();
    }

    normalized =
        normalized
            .replaceAll("\\b(ES|ESPANA|SPAIN)\\b$", "")
            .replaceAll("\\s+[0-9]{4,}$", "")
            .replaceAll("\\s+", " ")
            .trim();

    return normalized.isBlank() ? null : limit(normalized, 200);
  }

  private String chooseSource(String merchant, String description) {
    if (merchant == null || merchant.isBlank()) return description;
    String normalizedMerchant = ascii(merchant);
    if (normalizedMerchant.equals("PAYPAL")
        || normalizedMerchant.equals("STRIPE")
        || normalizedMerchant.equals("REDSYS")) {
      return description == null || description.isBlank() ? merchant : description;
    }
    return merchant;
  }

  private String ascii(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9*#&+./ -]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String limit(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max).trim();
  }

  private static Map<String, String> knownMerchants() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("MERCADONA", "MERCADONA");
    values.put("CARREFOUR", "CARREFOUR");
    values.put("LIDL", "LIDL");
    values.put("ALDI", "ALDI");
    values.put("REPSOL", "REPSOL");
    values.put("CEPSA", "CEPSA");
    values.put("MOEVE", "MOEVE");
    values.put("GALP", "GALP");
    values.put("NETFLIX", "NETFLIX");
    values.put("SPOTIFY", "SPOTIFY");
    values.put("DISNEY", "DISNEY+");
    values.put("HBO", "HBO");
    values.put("IBERDROLA", "IBERDROLA");
    values.put("ENDESA", "ENDESA");
    values.put("NATURGY", "NATURGY");
    values.put("AMZN", "AMAZON");
    values.put("AMAZON", "AMAZON");
    return Map.copyOf(values);
  }
}
