--liquibase formatted sql
--changeset ecl:005
CREATE TABLE tbl_lgd_curve (
    curve_id         BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_id         VARCHAR(32)    NOT NULL,
    collateral_type  VARCHAR(32)    NOT NULL,
    product_type     VARCHAR(32),
    lgd_base_value   DECIMAL(5,4)   NOT NULL,
    UNIQUE KEY uk_lgd_curve (scheme_id, group_id, collateral_type, product_type)
);

CREATE TABLE tbl_lgd_collateral_discount (
    discount_id      BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    collateral_type  VARCHAR(32)    NOT NULL,
    discount_rate    DECIMAL(5,4)   NOT NULL,
    UNIQUE KEY uk_discount (scheme_id, collateral_type)
);

CREATE TABLE tbl_lgd_depreciation (
    depreciation_id  BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    collateral_type  VARCHAR(32)    NOT NULL,
    year_offset      INT            NOT NULL,
    depreciation_rate DECIMAL(5,4)  NOT NULL,
    UNIQUE KEY uk_depreciation (scheme_id, collateral_type, year_offset)
);
