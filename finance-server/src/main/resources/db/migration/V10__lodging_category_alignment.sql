-- Move lodging subcategories mistakenly registered under Food to Leisure.
-- Keep their codes, because movements and classification rules reference these codes.
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT leisure.id, misplaced.code, misplaced.name, TRUE, 50
FROM category food
JOIN subcategory misplaced ON misplaced.category_id = food.id
JOIN category leisure ON leisure.code = 'OCIO'
LEFT JOIN subcategory existing
  ON existing.category_id = leisure.id AND existing.code = misplaced.code
WHERE food.code = 'ALIMENTACION'
  AND misplaced.code IN ('HOTELES', 'HOTEL', 'HOTLES', 'ALOJAMIENTO')
  AND existing.id IS NULL;

-- Include the canonical option even in installations where no hotel entry existed.
INSERT INTO subcategory (category_id, code, name, active, display_order)
SELECT leisure.id, 'HOTELES', 'Hoteles y alojamiento', TRUE, 50
FROM category leisure
WHERE leisure.code = 'OCIO'
  AND NOT EXISTS (
    SELECT 1 FROM subcategory existing
    WHERE existing.category_id = leisure.id AND existing.code = 'HOTELES'
  );

UPDATE subcategory
SET active = TRUE
WHERE category_id = (SELECT id FROM category WHERE code = 'OCIO')
  AND code IN ('HOTELES', 'HOTEL', 'HOTLES', 'ALOJAMIENTO');

-- Hide obsolete entries instead of deleting them, preserving historical IDs.
UPDATE subcategory
SET active = FALSE
WHERE category_id = (SELECT id FROM category WHERE code = 'ALIMENTACION')
  AND code IN ('HOTELES', 'HOTEL', 'HOTLES', 'ALOJAMIENTO');

-- The dashboard groups by movement.category, so adjust existing classifications too.
UPDATE movement
SET category = 'OCIO'
WHERE category = 'ALIMENTACION'
  AND subcategory IN ('HOTELES', 'HOTEL', 'HOTLES', 'ALOJAMIENTO');

UPDATE classification_rule
SET category = 'OCIO'
WHERE category = 'ALIMENTACION'
  AND subcategory IN ('HOTELES', 'HOTEL', 'HOTLES', 'ALOJAMIENTO');
