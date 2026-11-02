package com.performance.examples.sut;

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
     * Returns the device endpoint address (host:port) of an active device.
     * <p>
     * The address comes from the database ({@code devices.device_dns}); it is
     * the embedded WireMock endpoint in the example stack.
     */
    public Optional<String> findAddressByDeviceId(String deviceId) {
        List<String> results = jdbcTemplate.queryForList(
                "SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true",
                String.class, deviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public boolean existsByDeviceId(String deviceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)",
                Boolean.class, deviceId);
        return Boolean.TRUE.equals(exists);
    }
}
