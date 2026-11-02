package com.performance.examples.sut;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scenario 2: receives an HTTP event, checks the device in the database, then
 * publishes an event to Kafka.
 * <p>
 * Enabled by {@code sut.http-api.enabled=true} (default true).
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(prefix = "sut.http-api", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DeviceEventController {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventController.class);

    private final DeviceRepository deviceRepository;
    private final DeviceEventProducer deviceEventProducer;

    public DeviceEventController(DeviceRepository deviceRepository,
                                 DeviceEventProducer deviceEventProducer) {
        this.deviceRepository = deviceRepository;
        this.deviceEventProducer = deviceEventProducer;
    }

    /**
     * POST /api/events?device_id=device-0001
     */
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> submitEvent(
            @RequestParam("device_id") String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "device_id is required"));
        }

        boolean known = deviceRepository.existsByDeviceId(deviceId);
        deviceEventProducer.publish(deviceId, known);

        log.info("action=event_submitted device_id={} known={}", deviceId, known);

        return ResponseEntity.ok(Map.of(
                "device_id", deviceId,
                "known", known,
                "event_published", true));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
