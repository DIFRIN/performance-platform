-- Seed 10 000 active devices (classpath resource used by the `database`
-- POPULATE step of both example scenarios).
-- device_dns is the device endpoint address: the embedded WireMock published
-- on the host, reachable from the SUT container via host.docker.internal.

INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, GREATEST(4, LENGTH(gs::text)), '0'),
    'host.docker.internal:8090',
    TRUE
FROM generate_series(1, 10000) AS gs
ON CONFLICT (device_id) DO NOTHING;

SELECT COUNT(*) AS active_device_count
FROM devices
WHERE is_active = TRUE;
