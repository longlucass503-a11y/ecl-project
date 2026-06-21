--liquibase formatted sql
--changeset ecl:006
CREATE TABLE tbl_ccf_curve (
    curve_id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id            VARCHAR(32)    NOT NULL,
    product_type         VARCHAR(32)    NOT NULL,
    commitment_type      VARCHAR(32)    NOT NULL,
    commitment_days_min  INT            NOT NULL,
    commitment_days_max  INT            NOT NULL,
    ccf_value            DECIMAL(5,4)   NOT NULL,
    UNIQUE KEY uk_ccf (scheme_id, product_type, commitment_type, commitment_days_min, commitment_days_max)
);
