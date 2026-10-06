CREATE TABLE classification_rule(id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,priority INT NOT NULL,contains_text VARCHAR(200) NOT NULL,category VARCHAR(64) NOT NULL,kind VARCHAR(24) NOT NULL);
CREATE TABLE budget(month_code CHAR(7) NOT NULL,category VARCHAR(64) NOT NULL,amount DECIMAL(18,2) NOT NULL,PRIMARY KEY(month_code,category),CHECK(amount>=0));
