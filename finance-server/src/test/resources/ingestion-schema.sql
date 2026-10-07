DROP TABLE IF EXISTS movement;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS classification_rule;
CREATE TABLE product(id VARCHAR(64) PRIMARY KEY,name VARCHAR(100),type VARCHAR(16),currency CHAR(3),balance DECIMAL(18,2),balance_at TIMESTAMP,linked_account_id VARCHAR(64),credit_limit DECIMAL(18,2),provider VARCHAR(16),external_id VARCHAR(160),masked_pan VARCHAR(9),UNIQUE(provider,external_id));
CREATE TABLE movement(id CHAR(36) PRIMARY KEY,product_id VARCHAR(64),external_id VARCHAR(160),booking_date DATE,amount DECIMAL(18,2),currency CHAR(3),description VARCHAR(1000),merchant VARCHAR(200),normalized_merchant VARCHAR(200),category VARCHAR(64),subcategory VARCHAR(64),kind VARCHAR(24),status VARCHAR(16),classification_source VARCHAR(24),classification_confidence DECIMAL(5,4),UNIQUE(product_id,external_id));
CREATE TABLE classification_rule(id VARCHAR(64) PRIMARY KEY,priority INT,match_type VARCHAR(16),contains_text VARCHAR(200),category VARCHAR(64),subcategory VARCHAR(64),kind VARCHAR(24),confidence DECIMAL(5,4));
DROP TABLE IF EXISTS budget;
CREATE TABLE budget(month_code CHAR(7),category VARCHAR(64),amount DECIMAL(18,2),PRIMARY KEY(month_code,category));
