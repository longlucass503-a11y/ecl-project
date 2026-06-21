--liquibase formatted sql
--changeset ecl:008
CREATE TABLE tbl_ecl_job (
    job_id           VARCHAR(32)    NOT NULL,
    scheme_id        VARCHAR(32)    NOT NULL,
    calc_date        DATE           NOT NULL,
    trial_mode       TINYINT(1)     NOT NULL DEFAULT 0,
    status           VARCHAR(16)    NOT NULL,
    total_assets     INT,
    success_count    INT,
    exception_count  INT,
    started_at       DATETIME       NOT NULL,
    finished_at      DATETIME,
    duration_ms      BIGINT,
    error_summary    JSON,
    PRIMARY KEY (job_id)
);
