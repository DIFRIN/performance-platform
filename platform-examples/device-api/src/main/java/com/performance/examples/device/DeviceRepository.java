package com.performance.examples.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)
     */
    public boolean existsByDeviceId(String deviceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)",
                Boolean.class, deviceId);
        return Boolean.TRUE.equals(exists);
    }
}
