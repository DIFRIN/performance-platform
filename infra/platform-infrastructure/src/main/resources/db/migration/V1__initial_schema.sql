-- V1: complete initial schema
CREATE TABLE execution_state (
    id          VARCHAR(255)  PRIMARY KEY,
    scenario_id VARCHAR(255)  NOT NULL,
    status      VARCHAR(50)   NOT NULL,
    phases      JSONB         NOT NULL DEFAULT '{}',
    context     JSONB         NOT NULL DEFAULT '{}',
    planned_task_count INTEGER NOT NULL DEFAULT 0 CHECK (planned_task_count >= 0),
    tasks       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    scenario_yaml TEXT,
    scenario_details JSONB,
    started_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

-- Per-agent task results (composite PK for multi-claim).
CREATE TABLE task_result (
    execution_id VARCHAR(255)  NOT NULL,
    task_id      VARCHAR(255)  NOT NULL,
    agent_id     VARCHAR(255)  NOT NULL,
    status       VARCHAR(50)   NOT NULL,
    outputs      JSONB         NOT NULL DEFAULT '{}',
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (execution_id, task_id, agent_id)
);
