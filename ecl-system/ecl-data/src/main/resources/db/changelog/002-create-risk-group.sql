--liquibase formatted sql
--changeset ecl:002
CREATE TABLE tbl_risk_group (
    group_id         VARCHAR(32)    NOT NULL,
    group_code       VARCHAR(32)    NOT NULL,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_name       VARCHAR(64)    NOT NULL,
    sort_order       INT            NOT NULL DEFAULT 0,
    description      VARCHAR(200),
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME,
    PRIMARY KEY (group_id),
    UNIQUE KEY uk_scheme_group (scheme_id, group_id),
    FOREIGN KEY (scheme_id) REFERENCES tbl_ecl_scheme(scheme_id)
);

CREATE TABLE tbl_risk_group_detail (
    detail_id        BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_id         VARCHAR(32)    NOT NULL,
    priority         INT            NOT NULL,
    business_line    VARCHAR(32),
    customer_type    VARCHAR(32),
    product_type     VARCHAR(32),
    industry_code    VARCHAR(16),
    region_code      VARCHAR(16),
    collateral_type  VARCHAR(16),
    UNIQUE KEY uk_scheme_group_priority (scheme_id, group_id, priority)
);
