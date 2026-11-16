-- V2: persisted scheduled runs (one-shot and cron).
CREATE TABLE scheduled_run (
    id                  VARCHAR(255) PRIMARY KEY,
    scenario_id         VARCHAR(255) NOT NULL,
    scenario_yaml       TEXT         NOT NULL,
    schedule_kind       VARCHAR(20)  NOT NULL,
    schedule_expression VARCHAR(255) NOT NULL,
    next_run_at         TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    last_execution_id   VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_scheduled_run_due ON scheduled_run (status, next_run_at);
