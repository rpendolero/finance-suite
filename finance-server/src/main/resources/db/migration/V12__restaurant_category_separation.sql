-- Split restaurant spending from groceries without changing movement kind or manual metadata.
-- Existing catalog IDs for RESTAURANTES and COMIDA_DOMICILIO are preserved.
INSERT INTO category (code, name, active, display_order)
SELECT 'RESTAURACION', 'Restauración', TRUE, 35
WHERE NOT EXISTS (SELECT 1 FROM category WHERE code = 'RESTAURACION');

UPDATE category SET name = 'Restauración', active = TRUE
WHERE code = 'RESTAURACION';

-- Move existing subcategories rather than copying them: no obsolete entries
-- remain under ALIMENTACION and existing subcategory IDs remain stable.
UPDATE subcategory
SET category_id = (SELECT id FROM category WHERE code = 'RESTAURACION'),
    active = TRUE,
    display_order = CASE code
      WHEN 'RESTAURANTE' THEN 10
      WHEN 'RESTAURANTES' THEN 10
      WHEN 'COMIDA_DOMICILIO' THEN 30
      ELSE display_order
    END
WHERE category_id = (SELECT id FROM category WHERE code = 'ALIMENTACION')
  AND code IN ('RESTAURANTE', 'RESTAURANTES', 'COMIDA_DOMICILIO');

-- Ensure standard subcategories exist, including installations customized by users.
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'RESTAURANTES', 'Restaurantes', TRUE, 10
FROM category c WHERE c.code = 'RESTAURACION'
AND NOT EXISTS (
  SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'RESTAURANTES'
);

INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'BARES_CAFETERIAS', 'Bares y cafeterías', TRUE, 20
FROM category c WHERE c.code = 'RESTAURACION'
AND NOT EXISTS (
  SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'BARES_CAFETERIAS'
);

INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'COMIDA_DOMICILIO', 'Comida a domicilio', TRUE, 30
FROM category c WHERE c.code = 'RESTAURACION'
AND NOT EXISTS (
  SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'COMIDA_DOMICILIO'
);

INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT c.id, 'OTROS_ALIMENTOS', 'Otros alimentos', TRUE, 20
FROM category c WHERE c.code = 'ALIMENTACION'
AND NOT EXISTS (
  SELECT 1 FROM subcategory s WHERE s.category_id = c.id AND s.code = 'OTROS_ALIMENTOS'
);

UPDATE subcategory SET active = TRUE
WHERE category_id = (SELECT id FROM category WHERE code = 'RESTAURACION')
  AND code IN ('RESTAURANTES', 'BARES_CAFETERIAS', 'COMIDA_DOMICILIO');

-- Preserve classification source, confidence, kind, amounts and original subcategory.
-- Do not move unclassified or generic ALIMENTACION movements without evidence.
UPDATE movement SET category = 'RESTAURACION'
WHERE category = 'ALIMENTACION'
  AND subcategory IN ('RESTAURANTE', 'RESTAURANTES', 'COMIDA_DOMICILIO');

UPDATE classification_rule SET category = 'RESTAURACION'
WHERE category = 'ALIMENTACION'
  AND subcategory IN ('RESTAURANTE', 'RESTAURANTES', 'COMIDA_DOMICILIO');
