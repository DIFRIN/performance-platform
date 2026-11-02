-- Seed 10 000 active devices.
-- device_dns points at the embedded WireMock, reachable from the SUT container
-- through the host gateway (the mock port is published in both compose files).

INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, GREATEST(4, LENGTH(gs::text)), '0'),
    'host.docker.internal:8090',
    TRUE
FROM generate_series(1, 10000) AS gs
ON CONFLICT (device_id) DO NOTHING;

DO $$
DECLARE
    device_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO device_count FROM devices WHERE is_active = TRUE;
    IF device_count < 10000 THEN
        RAISE WARNING 'Expected 10000 active devices, found %', device_count;
    ELSE
        RAISE NOTICE 'Seed OK: % active devices', device_count;
    END IF;
END $$;
