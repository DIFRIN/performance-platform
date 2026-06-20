-- Device table for performance test scenarios
-- Created automatically by postgres docker-entrypoint-initdb.d
CREATE TABLE IF NOT EXISTS devices (
    device_id   VARCHAR(64) PRIMARY KEY,
    status      VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index on status for fast COUNT filtering
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
