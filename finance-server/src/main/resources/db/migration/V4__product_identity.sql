-- Existing product IDs and movement foreign keys remain unchanged.
-- masked_pan is display metadata, never a unique card identifier.
ALTER TABLE product
 ADD COLUMN external_id VARCHAR(160) COLLATE utf8mb4_bin NULL,
 ADD COLUMN masked_pan VARCHAR(9) NULL,
 ADD UNIQUE KEY uk_product_provider_external (provider, external_id);
