INSERT INTO category (code, name, display_order) VALUES ('NO_COMPUTABLE', 'No computable', 150);
INSERT INTO subcategory (category_id, code, name, display_order)
SELECT id, 'LIQUIDACION_TARJETA', 'Liquidación de tarjeta', 10 FROM category WHERE code = 'NO_COMPUTABLE';
INSERT INTO subcategory (category_id, code, name, display_order)
SELECT id, 'LIQUIDACION_PAYPAL', 'Liquidación de PayPal', 20 FROM category WHERE code = 'NO_COMPUTABLE';
INSERT INTO subcategory (category_id, code, name, display_order)
SELECT id, 'TRASPASO_INTERNO', 'Traspaso entre cuentas propias', 30 FROM category WHERE code = 'NO_COMPUTABLE';
INSERT INTO subcategory (category_id, code, name, display_order)
SELECT id, 'MOVIMIENTO_DUPLICADO', 'Movimiento duplicado', 40 FROM category WHERE code = 'NO_COMPUTABLE';
INSERT INTO subcategory (category_id, code, name, display_order)
SELECT id, 'OTROS_NO_COMPUTABLES', 'Otros no computables', 50 FROM category WHERE code = 'NO_COMPUTABLE';
UPDATE subcategory SET active = FALSE WHERE category_id IN (SELECT id FROM category WHERE code = 'TRANSFERENCIAS')
AND code IN ('LIQUIDACION_TARJETA', 'LIQUIDACION_PAYPAL', 'LIQUIDACION_MONEDERO', 'TRASPASO_INTERNO');

-- Preserve movement kind and manual classification metadata.
UPDATE movement SET category = 'NO_COMPUTABLE', subcategory = CASE kind WHEN 'CARD_SETTLEMENT' THEN 'LIQUIDACION_TARJETA' WHEN 'WALLET_SETTLEMENT' THEN 'LIQUIDACION_PAYPAL' WHEN 'INTERNAL_TRANSFER' THEN 'TRASPASO_INTERNO' WHEN 'DUPLICATE' THEN 'MOVIMIENTO_DUPLICADO' WHEN 'NON_COMPUTABLE' THEN 'OTROS_NO_COMPUTABLES' END
WHERE kind IN ('CARD_SETTLEMENT', 'WALLET_SETTLEMENT', 'INTERNAL_TRANSFER', 'DUPLICATE', 'NON_COMPUTABLE');

UPDATE classification_rule SET category = 'NO_COMPUTABLE', subcategory = CASE kind WHEN 'CARD_SETTLEMENT' THEN 'LIQUIDACION_TARJETA' WHEN 'WALLET_SETTLEMENT' THEN 'LIQUIDACION_PAYPAL' WHEN 'INTERNAL_TRANSFER' THEN 'TRASPASO_INTERNO' WHEN 'DUPLICATE' THEN 'MOVIMIENTO_DUPLICADO' WHEN 'NON_COMPUTABLE' THEN 'OTROS_NO_COMPUTABLES' END
WHERE kind IN ('CARD_SETTLEMENT', 'WALLET_SETTLEMENT', 'INTERNAL_TRANSFER', 'DUPLICATE', 'NON_COMPUTABLE');
