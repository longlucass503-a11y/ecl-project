--liquibase formatted sql
--changeset ecl:007
CREATE TABLE tbl_overlay_rule (
    rule_id            BIGINT         AUTO_INCREMENT PRIMARY KEY,
    scheme_id          VARCHAR(32)    NOT NULL,
    group_id           VARCHAR(32)    NOT NULL,
    overlay_type       VARCHAR(16)    NOT NULL,
    adjustment_target  VARCHAR(16)    NOT NULL DEFAULT 'ECL_FINAL',
    adjustment_type    VARCHAR(16)    NOT NULL,
    adjustment_value   DECIMAL(12,4)  NOT NULL,
    priority           INT            NOT NULL,
    conditions         JSON           NOT NULL,
    effective_date     DATE           NOT NULL,
    expiry_date        DATE,
    INDEX idx_overlay_scheme (scheme_id),
    UNIQUE KEY uk_overlay_rule (scheme_id, group_id, overlay_type, adjustment_target, adjustment_type, priority)
);
