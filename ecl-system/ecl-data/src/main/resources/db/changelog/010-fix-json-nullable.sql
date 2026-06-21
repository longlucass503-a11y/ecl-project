--liquibase formatted sql
--changeset ecl:010
ALTER TABLE tbl_stage_rule MODIFY COLUMN conditions JSON NULL;
ALTER TABLE tbl_overlay_rule MODIFY COLUMN conditions JSON NULL;
ALTER TABLE tbl_ecl_calc_detail MODIFY COLUMN input_data JSON NULL;
