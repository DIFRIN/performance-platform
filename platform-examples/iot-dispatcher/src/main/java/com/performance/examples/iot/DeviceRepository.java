package com.performance.examples.iot;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the device_dns of the device if known and active.
     * SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true
     */
    public Optional<String> findDnsByDeviceId(String deviceId) {
        List<String> results = jdbcTemplate.queryForList(
                "SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true",
                String.class, deviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
