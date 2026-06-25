--liquibase formatted sql
--changeset ecl:013
ALTER TABLE tbl_ecl_calc_detail
    ADD COLUMN ead_breakdown JSON NULL AFTER ead_exception,
    ADD COLUMN lgd_details JSON NULL AFTER lgd_exception;

ALTER TABLE tbl_lgd_collateral_discount
    DROP INDEX uk_discount,
    ADD UNIQUE KEY uk_discount (scheme_id, collateral_category, collateral_type);
