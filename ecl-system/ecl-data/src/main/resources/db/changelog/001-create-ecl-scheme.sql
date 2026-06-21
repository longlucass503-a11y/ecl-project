--liquibase formatted sql
--changeset ecl:001
CREATE TABLE tbl_ecl_scheme (
    scheme_id        VARCHAR(32)    NOT NULL,
    scheme_code      VARCHAR(32)    NOT NULL,
    scheme_name      VARCHAR(128)   NOT NULL,
    scheme_version   VARCHAR(16)    NOT NULL,
    status           VARCHAR(16)    NOT NULL DEFAULT 'DRAFT',
    effective_date   DATE,
    effective_at     DATETIME,
    expired_at       DATETIME,
    discount_rate    DECIMAL(5,4)   NOT NULL DEFAULT 0.0500,
    default_ccf      DECIMAL(5,4)   NOT NULL DEFAULT 0.0000,
    default_lgd      DECIMAL(5,4)   NOT NULL DEFAULT 0.4500,
    created_by       VARCHAR(64)    NOT NULL,
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64),
    updated_at       DATETIME,
    description      VARCHAR(500),
    PRIMARY KEY (scheme_id)
);
