-- V1: execution_state — global execution lifecycle tracking
CREATE TABLE execution_state (
    id          VARCHAR(255)  PRIMARY KEY,
    scenario_id VARCHAR(255)  NOT NULL,
    status      VARCHAR(50)   NOT NULL,
    phases      JSONB         NOT NULL DEFAULT '{}',
    context     JSONB         NOT NULL DEFAULT '{}',
    started_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);
