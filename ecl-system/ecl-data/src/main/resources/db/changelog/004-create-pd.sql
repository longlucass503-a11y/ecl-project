--liquibase formatted sql
--changeset ecl:004
CREATE TABLE tbl_pd_scenario (
    scenario_id      BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    scenario_type    VARCHAR(32)    NOT NULL,
    scenario_name    VARCHAR(64),
    weight           DECIMAL(5,2)   NOT NULL,
    UNIQUE KEY uk_scenario (scheme_id, scenario_type)
);

CREATE TABLE tbl_pd_curve (
    curve_id         BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_id         VARCHAR(32)    NOT NULL,
    scenario_id      BIGINT         NOT NULL,
    rating_code      VARCHAR(16)    NOT NULL,
    pd_value         DECIMAL(12,8)  NOT NULL,
    UNIQUE KEY uk_pd_curve (scheme_id, group_id, scenario_id, rating_code)
);
