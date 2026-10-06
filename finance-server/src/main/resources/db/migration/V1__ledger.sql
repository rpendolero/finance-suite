CREATE TABLE product (
 id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 name VARCHAR(100) NOT NULL,type VARCHAR(16) NOT NULL,currency CHAR(3) NOT NULL,
 balance DECIMAL(18,2) NOT NULL,balance_at TIMESTAMP(6) NOT NULL,
 linked_account_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,credit_limit DECIMAL(18,2) NULL,
 FOREIGN KEY(linked_account_id) REFERENCES product(id),
 CHECK(type IN ('ACCOUNT','DEBIT_CARD','CREDIT_CARD','WALLET')),CHECK(currency='EUR')
);
CREATE TABLE movement (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 product_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 external_id VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
 booking_date DATE NOT NULL,amount DECIMAL(18,2) NOT NULL,currency CHAR(3) NOT NULL,
 description VARCHAR(1000) NOT NULL,merchant VARCHAR(200),category VARCHAR(64) NOT NULL,
 kind VARCHAR(24) NOT NULL,status VARCHAR(16) NOT NULL,
 FOREIGN KEY(product_id) REFERENCES product(id),UNIQUE KEY uk_product_external(product_id,external_id),
 INDEX ix_date_product(booking_date,product_id),CHECK(currency='EUR'),
 CHECK(kind IN ('NORMAL','REFUND','INTERNAL_TRANSFER','CARD_SETTLEMENT','DUPLICATE')),CHECK(status IN ('BOOKED','PENDING'))
);
