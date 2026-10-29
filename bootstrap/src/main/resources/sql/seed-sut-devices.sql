
-- V2__seed_10k_devices.sql
-- Insère 10 000 devices de test
-- device_dns = WireMock (docker-compose-sut)

INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, GREATEST(4, LENGTH(gs::text)), '0'),
    'wiremock:8080',
    TRUE
FROM generate_series(1, 10000) AS gs
ON CONFLICT (device_id) DO NOTHING;

-- Vérification du nombre de devices actifs
SELECT COUNT(*) AS active_device_count
FROM devices
WHERE is_active = TRUE;
