-- V1__devices_schema.sql
-- Table centrale des devices IoT pour les tests de performance
-- Partagée par iot-dispatcher et device-api

CREATE TABLE IF NOT EXISTS devices (
    id         SERIAL PRIMARY KEY,
    device_id  VARCHAR(50)  NOT NULL,
    device_dns VARCHAR(255) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_devices_device_id UNIQUE (device_id)
);

-- Index pour les lookups par device_id (chemin critique : O(1))
CREATE INDEX IF NOT EXISTS idx_devices_device_id
    ON devices(device_id);

-- Index partiel pour les devices actifs seulement
CREATE INDEX IF NOT EXISTS idx_devices_active
    ON devices(device_id)
    WHERE is_active = true;

COMMENT ON TABLE  devices            IS 'Registre des devices IoT — SUT pour tests de performance';
COMMENT ON COLUMN devices.device_id  IS 'Identifiant unique du device (ex: device-0001)';
COMMENT ON COLUMN devices.device_dns IS 'Adresse DNS/IP du device pour les commandes HTTP';
COMMENT ON COLUMN devices.is_active  IS 'false = device hors service, ignoré par iot-dispatcher';
