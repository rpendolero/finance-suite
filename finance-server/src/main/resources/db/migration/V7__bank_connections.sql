CREATE TABLE bank_connection (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 provider VARCHAR(32) NOT NULL,
 bank_name VARCHAR(100) NOT NULL,
 country CHAR(2) NOT NULL,
 external_session_id VARCHAR(160) NULL,
 authorization_state VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 valid_until TIMESTAMP(6) NULL,
 status VARCHAR(24) NOT NULL,
 last_sync_at TIMESTAMP(6) NULL,
 UNIQUE KEY uk_bank_connection_state(authorization_state)
);

CREATE TABLE external_bank_account (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 connection_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 external_account_id VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
 product_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 name VARCHAR(100) NOT NULL,
 currency CHAR(3) NOT NULL,
 CONSTRAINT fk_external_account_connection FOREIGN KEY(connection_id) REFERENCES bank_connection(id),
 CONSTRAINT fk_external_account_product FOREIGN KEY(product_id) REFERENCES product(id),
 UNIQUE KEY uk_external_bank_account(connection_id, external_account_id),
 CHECK(currency='EUR')
);
