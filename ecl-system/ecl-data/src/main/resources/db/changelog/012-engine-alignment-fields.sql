--liquibase formatted sql
--changeset ecl:012
ALTER TABLE tbl_risk_group_detail
    DROP COLUMN customer_type,
    DROP COLUMN region_code;

ALTER TABLE tbl_crr_rating_drop_rule
    ADD COLUMN rating_system VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_CRR' AFTER group_id,
    ADD COLUMN rating_agency VARCHAR(64) NOT NULL DEFAULT 'INTERNAL_CRR' AFTER rating_system,
    DROP INDEX uk_drop_rule,
    ADD UNIQUE KEY uk_drop_rule (scheme_id, group_id, rating_system, rating_agency, current_rating);

ALTER TABLE tbl_pd_curve
    ADD COLUMN rating_system VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_CRR' AFTER scenario_id,
    ADD COLUMN rating_agency VARCHAR(64) NOT NULL DEFAULT 'INTERNAL_CRR' AFTER rating_system,
    DROP INDEX uk_pd_curve,
    ADD UNIQUE KEY uk_pd_curve (scheme_id, group_id, scenario_id, rating_system, rating_agency, rating_code);

ALTER TABLE tbl_ecl_scheme
    ADD COLUMN lgd_floor DECIMAL(5,4) NOT NULL DEFAULT 0.1000 AFTER default_lgd;
