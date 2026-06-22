package com.performance.examples.iot;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeviceCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandConsumer.class);

    private final DeviceRepository deviceRepository;
    private final IotHttpClient iotHttpClient;

    public DeviceCommandConsumer(DeviceRepository deviceRepository, IotHttpClient iotHttpClient) {
        this.deviceRepository = deviceRepository;
        this.iotHttpClient = iotHttpClient;
    }

    @KafkaListener(
        topics     = "${iot.topics.commands:iot-commands}",
        groupId    = "${iot.kafka.group-id:iot-dispatcher}",
        concurrency = "${iot.kafka.concurrency:3}"
    )
    public void handle(String message) {
        try {
            // 1. Parse minimal JSON: {"device_id":"xxx","command":"ping"}
            String deviceId = extractDeviceId(message);
            if (deviceId == null) {
                log.warn("action=skip_message reason=no_device_id message={}", message);
                return;
            }

            // 2. Lookup in DB
            Optional<String> dns = deviceRepository.findDnsByDeviceId(deviceId);
            if (dns.isEmpty()) {
                log.warn("action=skip_message reason=unknown_device device_id={}", deviceId);
                return;
            }

            // 3. Dispatch HTTP
            int status = iotHttpClient.sendCommand(dns.get(), message);
            log.info("action=command_dispatched device_id={} dns={} status={}", deviceId, dns.get(), status);

        } catch (Exception e) {
            log.error("action=dispatch_error message={}", message, e);
            // No rethrow — message is acked (no infinite Kafka retry)
        }
    }

    private String extractDeviceId(String json) {
        // Minimal JSON extraction (no JSON lib to keep dependencies light)
        // Pattern: "device_id":"value"
        int idx = json.indexOf("\"device_id\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1) + 1;
        int end   = json.indexOf('"', start);
        return start > 0 && end > start ? json.substring(start, end) : null;
    }
}
