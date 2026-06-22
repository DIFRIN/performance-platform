package com.performance.examples.device;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
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
     * Verifie si le device est connu, publie dans Kafka, retourne le resultat.
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
                "device_id",       deviceId,
                "known",           known,
                "event_published", true
        ));
    }

    // Health endpoint leger pour les warmup checks
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
