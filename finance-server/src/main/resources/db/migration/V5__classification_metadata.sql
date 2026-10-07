ALTER TABLE movement
  ADD COLUMN normalized_merchant VARCHAR(200) NULL AFTER merchant,
  ADD COLUMN subcategory VARCHAR(64) NULL AFTER category,
  ADD COLUMN classification_source VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED' AFTER status,
  ADD COLUMN classification_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000 AFTER classification_source;

UPDATE movement
SET normalized_merchant = UPPER(TRIM(merchant))
WHERE merchant IS NOT NULL AND normalized_merchant IS NULL;

UPDATE movement
SET classification_source =
      CASE WHEN UPPER(category) = 'UNCLASSIFIED' THEN 'UNCLASSIFIED' ELSE 'AUTOMATIC' END,
    classification_confidence =
      CASE WHEN UPPER(category) = 'UNCLASSIFIED' THEN 0.0000 ELSE 0.5000 END;

ALTER TABLE classification_rule
  ADD COLUMN match_type VARCHAR(16) NOT NULL DEFAULT 'CONTAINS' AFTER priority,
  ADD COLUMN subcategory VARCHAR(64) NULL AFTER category,
  ADD COLUMN confidence DECIMAL(5,4) NOT NULL DEFAULT 0.9000 AFTER kind;

INSERT IGNORE INTO classification_rule
  (id, priority, match_type, contains_text, category, subcategory, kind, confidence)
VALUES
  ('sys_internal_transfer', 1, 'CONTAINS', 'TRASPASO ENTRE CUENTAS', 'TRANSFERENCIAS', 'TRASPASO_INTERNO', 'INTERNAL_TRANSFER', 0.9800),
  ('sys_internal_transfer_alt', 2, 'CONTAINS', 'TRANSFERENCIA ENTRE CUENTAS', 'TRANSFERENCIAS', 'TRASPASO_INTERNO', 'INTERNAL_TRANSFER', 0.9800),
  ('sys_card_settlement', 3, 'CONTAINS', 'LIQUIDACION TARJETA', 'TRANSFERENCIAS', 'LIQUIDACION_TARJETA', 'CARD_SETTLEMENT', 0.9800),
  ('sys_card_settlement_alt', 4, 'CONTAINS', 'PAGO MENSUAL TARJETA', 'TRANSFERENCIAS', 'LIQUIDACION_TARJETA', 'CARD_SETTLEMENT', 0.9800),
  ('sys_salary', 20, 'CONTAINS', 'NOMINA', 'INGRESOS', 'NOMINA', 'NORMAL', 0.9500),
  ('sys_mercadona', 100, 'MERCHANT', 'MERCADONA', 'ALIMENTACION', 'SUPERMERCADO', 'NORMAL', 0.9900),
  ('sys_carrefour', 101, 'MERCHANT', 'CARREFOUR', 'ALIMENTACION', 'SUPERMERCADO', 'NORMAL', 0.9900),
  ('sys_lidl', 102, 'MERCHANT', 'LIDL', 'ALIMENTACION', 'SUPERMERCADO', 'NORMAL', 0.9900),
  ('sys_aldi', 103, 'MERCHANT', 'ALDI', 'ALIMENTACION', 'SUPERMERCADO', 'NORMAL', 0.9900),
  ('sys_repsol', 120, 'MERCHANT', 'REPSOL', 'TRANSPORTE', 'COMBUSTIBLE', 'NORMAL', 0.9900),
  ('sys_cepsa', 121, 'MERCHANT', 'CEPSA', 'TRANSPORTE', 'COMBUSTIBLE', 'NORMAL', 0.9900),
  ('sys_moeve', 122, 'MERCHANT', 'MOEVE', 'TRANSPORTE', 'COMBUSTIBLE', 'NORMAL', 0.9900),
  ('sys_galp', 123, 'MERCHANT', 'GALP', 'TRANSPORTE', 'COMBUSTIBLE', 'NORMAL', 0.9900),
  ('sys_netflix', 140, 'MERCHANT', 'NETFLIX', 'SUSCRIPCIONES', 'STREAMING', 'NORMAL', 0.9900),
  ('sys_spotify', 141, 'MERCHANT', 'SPOTIFY', 'SUSCRIPCIONES', 'STREAMING', 'NORMAL', 0.9900),
  ('sys_disney', 142, 'MERCHANT', 'DISNEY+', 'SUSCRIPCIONES', 'STREAMING', 'NORMAL', 0.9900),
  ('sys_hbo', 143, 'MERCHANT', 'HBO', 'SUSCRIPCIONES', 'STREAMING', 'NORMAL', 0.9900),
  ('sys_iberdrola', 160, 'MERCHANT', 'IBERDROLA', 'VIVIENDA', 'ELECTRICIDAD', 'NORMAL', 0.9900),
  ('sys_endesa', 161, 'MERCHANT', 'ENDESA', 'VIVIENDA', 'ELECTRICIDAD', 'NORMAL', 0.9900),
  ('sys_amazon', 180, 'MERCHANT', 'AMAZON', 'COMPRAS', 'ONLINE', 'NORMAL', 0.9900);
