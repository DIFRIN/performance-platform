-- V2: task_result — per-agent task results (composite PK for multi-claim)
CREATE TABLE task_result (
    execution_id VARCHAR(255)  NOT NULL,
    task_id      VARCHAR(255)  NOT NULL,
    agent_id     VARCHAR(255)  NOT NULL,
    status       VARCHAR(50)   NOT NULL,
    outputs      JSONB         NOT NULL DEFAULT '{}',
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (execution_id, task_id, agent_id)
);
