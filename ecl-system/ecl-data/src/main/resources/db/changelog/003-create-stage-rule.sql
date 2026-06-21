--liquibase formatted sql
--changeset ecl:003
CREATE TABLE tbl_stage_rule (
    rule_id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_id         VARCHAR(32)    NOT NULL,
    rule_type        VARCHAR(16)    NOT NULL,
    stage_from       VARCHAR(8),
    stage_to         VARCHAR(8)     NOT NULL,
    priority         INT            NOT NULL,
    observation_days INT,
    conditions       JSON           NOT NULL,
    UNIQUE KEY uk_stage_rule (scheme_id, group_id, rule_type, stage_from, stage_to, priority)
);

CREATE TABLE tbl_crr_rating_drop_rule (
    drop_rule_id     BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id        VARCHAR(32)    NOT NULL,
    group_id         VARCHAR(32)    NOT NULL,
    current_rating   VARCHAR(16)    NOT NULL,
    drop_threshold   INT            NOT NULL,
    UNIQUE KEY uk_drop_rule (scheme_id, group_id, current_rating)
);
