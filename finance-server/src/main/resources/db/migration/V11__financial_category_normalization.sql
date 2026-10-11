-- Normalize only unambiguous category mappings. Never discard manual classification metadata.
-- Rows in legacy mixed-purpose buckets are intentionally retained for manual review.
INSERT INTO category (code, name, active, display_order)
SELECT 'AHORRO_E_INVERSION', 'Ahorro e inversión', TRUE, 160
WHERE NOT EXISTS (SELECT 1 FROM category WHERE code = 'AHORRO_E_INVERSION' OR name = 'Ahorro e inversión');
INSERT INTO category (code, name, active, display_order)
SELECT 'FINANCIACION', 'Financiación', TRUE, 170
WHERE NOT EXISTS (SELECT 1 FROM category WHERE code = 'FINANCIACION' OR name = 'Financiación');
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'ALQUILER', 'Alquiler', TRUE, 80 FROM category c
WHERE c.code = 'VIVIENDA' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'ALQUILER'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'SERVICIO_DOMESTICO', 'Servicio doméstico', TRUE, 90 FROM category c
WHERE c.code = 'VIVIENDA' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'SERVICIO_DOMESTICO'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'TELEFONIA', 'Telefonía', TRUE, 100 FROM category c
WHERE c.code = 'VIVIENDA' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'TELEFONIA'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'REGALOS', 'Regalos', TRUE, 60 FROM category c
WHERE c.code = 'COMPRAS' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'REGALOS'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'MULTAS', 'Multas y sanciones', TRUE, 20 FROM category c
WHERE c.code = 'OTROS' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'MULTAS'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'HIPOTECA', 'Hipoteca', TRUE, 10 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'HIPOTECA'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'VEHICULO', 'Vehículo', TRUE, 20 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'VEHICULO'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'PRESTAMO_PERSONAL', 'Préstamo personal', TRUE, 30 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'PRESTAMO_PERSONAL'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'APLAZAMIENTO', 'Aplazamiento', TRUE, 40 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'APLAZAMIENTO'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'AMORTIZACION_CAPITAL', 'Amortización de capital (desglosada)', TRUE, 50 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'AMORTIZACION_CAPITAL'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'INTERESES', 'Intereses (desglosados)', TRUE, 60 FROM category c
WHERE c.code = 'FINANCIACION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'INTERESES'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'PLAN_DE_PENSIONES', 'Plan de pensiones', TRUE, 10 FROM category c
WHERE c.code = 'AHORRO_E_INVERSION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'PLAN_DE_PENSIONES'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'FONDOS_INVERSION', 'Fondos de inversión', TRUE, 20 FROM category c
WHERE c.code = 'AHORRO_E_INVERSION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'FONDOS_INVERSION'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'ETF', 'ETF', TRUE, 30 FROM category c
WHERE c.code = 'AHORRO_E_INVERSION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'ETF'
);
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'OTRO_AHORRO', 'Otro ahorro', TRUE, 40 FROM category c
WHERE c.code = 'AHORRO_E_INVERSION' AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'OTRO_AHORRO'
);
-- OCIO/REGALOS -> COMPRAS/REGALOS, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'COMPRAS', subcategory = 'REGALOS'
WHERE category = 'OCIO' AND subcategory = 'REGALOS';
UPDATE classification_rule SET category = 'COMPRAS', subcategory = 'REGALOS'
WHERE category = 'OCIO' AND subcategory = 'REGALOS';
UPDATE subcategory SET active = FALSE WHERE code = 'REGALOS'
 AND category_id = (SELECT id FROM category WHERE code = 'OCIO');
UPDATE subcategory SET active = TRUE WHERE code = 'REGALOS'
 AND category_id = (SELECT id FROM category WHERE code = 'COMPRAS');
-- TRANSPORTE/FINANCIACION_VEHICULO -> FINANCIACION/VEHICULO, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'FINANCIACION', subcategory = 'VEHICULO'
WHERE category = 'TRANSPORTE' AND subcategory = 'FINANCIACION_VEHICULO';
UPDATE classification_rule SET category = 'FINANCIACION', subcategory = 'VEHICULO'
WHERE category = 'TRANSPORTE' AND subcategory = 'FINANCIACION_VEHICULO';
UPDATE subcategory SET active = FALSE WHERE code = 'FINANCIACION_VEHICULO'
 AND category_id = (SELECT id FROM category WHERE code = 'TRANSPORTE');
UPDATE subcategory SET active = TRUE WHERE code = 'VEHICULO'
 AND category_id = (SELECT id FROM category WHERE code = 'FINANCIACION');
-- VIVIENDA/HIPOTECA -> FINANCIACION/HIPOTECA, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'FINANCIACION', subcategory = 'HIPOTECA'
WHERE category = 'VIVIENDA' AND subcategory = 'HIPOTECA';
UPDATE classification_rule SET category = 'FINANCIACION', subcategory = 'HIPOTECA'
WHERE category = 'VIVIENDA' AND subcategory = 'HIPOTECA';
UPDATE subcategory SET active = FALSE WHERE code = 'HIPOTECA'
 AND category_id = (SELECT id FROM category WHERE code = 'VIVIENDA');
UPDATE subcategory SET active = TRUE WHERE code = 'HIPOTECA'
 AND category_id = (SELECT id FROM category WHERE code = 'FINANCIACION');
-- SUSCRIPCIONES/TELEFONIA -> VIVIENDA/TELEFONIA, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'VIVIENDA', subcategory = 'TELEFONIA'
WHERE category = 'SUSCRIPCIONES' AND subcategory = 'TELEFONIA';
UPDATE classification_rule SET category = 'VIVIENDA', subcategory = 'TELEFONIA'
WHERE category = 'SUSCRIPCIONES' AND subcategory = 'TELEFONIA';
UPDATE subcategory SET active = FALSE WHERE code = 'TELEFONIA'
 AND category_id = (SELECT id FROM category WHERE code = 'SUSCRIPCIONES');
UPDATE subcategory SET active = TRUE WHERE code = 'TELEFONIA'
 AND category_id = (SELECT id FROM category WHERE code = 'VIVIENDA');
-- IMPUESTOS/MULTAS -> OTROS/MULTAS, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'OTROS', subcategory = 'MULTAS'
WHERE category = 'IMPUESTOS' AND subcategory = 'MULTAS';
UPDATE classification_rule SET category = 'OTROS', subcategory = 'MULTAS'
WHERE category = 'IMPUESTOS' AND subcategory = 'MULTAS';
UPDATE subcategory SET active = FALSE WHERE code = 'MULTAS'
 AND category_id = (SELECT id FROM category WHERE code = 'IMPUESTOS');
UPDATE subcategory SET active = TRUE WHERE code = 'MULTAS'
 AND category_id = (SELECT id FROM category WHERE code = 'OTROS');
-- HOGAR/SERVICIO_DOMESTICO -> VIVIENDA/SERVICIO_DOMESTICO, preserving kind, source, confidence and rule ID.
UPDATE movement SET category = 'VIVIENDA', subcategory = 'SERVICIO_DOMESTICO'
WHERE category = 'HOGAR' AND subcategory = 'SERVICIO_DOMESTICO';
UPDATE classification_rule SET category = 'VIVIENDA', subcategory = 'SERVICIO_DOMESTICO'
WHERE category = 'HOGAR' AND subcategory = 'SERVICIO_DOMESTICO';
UPDATE subcategory SET active = FALSE WHERE code = 'SERVICIO_DOMESTICO'
 AND category_id = (SELECT id FROM category WHERE code = 'HOGAR');
UPDATE subcategory SET active = TRUE WHERE code = 'SERVICIO_DOMESTICO'
 AND category_id = (SELECT id FROM category WHERE code = 'VIVIENDA');
UPDATE category SET active = FALSE
WHERE code = 'HOGAR'
AND NOT EXISTS (
 SELECT 1 FROM subcategory s WHERE s.category_id = category.id AND s.active = TRUE
)
AND NOT EXISTS (SELECT 1 FROM movement m WHERE m.category = 'HOGAR')
AND NOT EXISTS (SELECT 1 FROM classification_rule r WHERE r.category = 'HOGAR');
-- DO NOT bulk-move VIVIENDA/HIPOTECA_ALQUILER: the historic entries may be rent OR debt.
-- DO NOT bulk-move COMPRAS/ONLINE: the purchase type cannot be deduced from its channel.
-- DO NOT exclude EFECTIVO/RETIRADA_CAJERO: not all cash spending is separately recorded.
-- Refunds under INGRESOS/DEVOLUCIONES are handled by the analytical service without inventing an original purchase category.
