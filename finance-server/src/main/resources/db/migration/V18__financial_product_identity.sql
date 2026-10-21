-- Expand-only migration. Existing product identifiers and movement relationships are preserved.
-- UUID replacement and duplicate consolidation require a separate audited data migration.
ALTER TABLE external_bank_account ADD COLUMN iban VARCHAR(34) NULL;
CREATE INDEX idx_external_bank_account_iban ON external_bank_account (iban);

CREATE TABLE financial_product_identity (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 product_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 identity_type VARCHAR(24) NOT NULL,
 identity_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 masked_identifier VARCHAR(40) NULL,
 issuer VARCHAR(100) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_financial_identity_product FOREIGN KEY (product_id) REFERENCES product(id),
 CONSTRAINT uk_financial_identity UNIQUE (identity_type, identity_hash),
 INDEX idx_financial_identity_product (product_id)
);

CREATE TABLE product_id_migration (
 old_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 new_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 migrated_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_product_id_migration_new UNIQUE (new_id)
);
