-- Device registry used by both example scenarios.
-- The same table is read by the SUT (Kafka -> HTTP and HTTP -> Kafka paths)
-- and by the platform database steps/assertions.

CREATE TABLE IF NOT EXISTS devices (
    id         SERIAL PRIMARY KEY,
    device_id  VARCHAR(50)  NOT NULL,
    device_dns VARCHAR(255) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_devices_device_id UNIQUE (device_id)
);

CREATE INDEX IF NOT EXISTS idx_devices_device_id
    ON devices(device_id);

CREATE INDEX IF NOT EXISTS idx_devices_active
    ON devices(device_id)
    WHERE is_active = true;

COMMENT ON COLUMN devices.device_dns IS
    'Device endpoint address (host:port) resolved by the SUT; points at the embedded WireMock in this example';
