-- V2__seed_10k_devices.sql
-- Insère 10 000 devices de test
-- device_dns = WireMock (simule les endpoints IoT dans le docker-compose-sut)
-- device-0001 → device-9999 + device-10000

INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, 4, '0'),
    'wiremock:8080',
    true
FROM generate_series(1, 10000) gs
ON CONFLICT (device_id) DO NOTHING;

-- Vérification
DO $$
DECLARE
    device_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO device_count FROM devices WHERE is_active = true;
    IF device_count < 10000 THEN
        RAISE WARNING 'Expected 10000 active devices, found %', device_count;
    ELSE
        RAISE NOTICE 'Seed OK: % active devices', device_count;
    END IF;
END $$;
