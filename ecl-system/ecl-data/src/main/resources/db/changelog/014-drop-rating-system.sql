--liquibase formatted sql
--changeset ecl:014
ALTER TABLE tbl_crr_rating_drop_rule
    DROP INDEX uk_drop_rule,
    ADD UNIQUE KEY uk_drop_rule (scheme_id, group_id, rating_agency, current_rating),
    DROP COLUMN rating_system;

ALTER TABLE tbl_pd_curve
    DROP INDEX uk_pd_curve,
    ADD UNIQUE KEY uk_pd_curve (scheme_id, group_id, scenario_id, rating_agency, rating_code),
    DROP COLUMN rating_system;
