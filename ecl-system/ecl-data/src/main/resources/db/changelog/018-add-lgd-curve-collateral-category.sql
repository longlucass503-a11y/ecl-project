--liquibase formatted sql
--changeset ecl:018
ALTER TABLE tbl_lgd_curve
    ADD COLUMN collateral_category VARCHAR(32) NULL AFTER collateral_type;

ALTER TABLE tbl_lgd_curve
    DROP INDEX uk_lgd_curve;

ALTER TABLE tbl_lgd_curve
    ADD UNIQUE KEY uk_lgd_curve (scheme_id, group_id, collateral_type, collateral_category, product_type);
