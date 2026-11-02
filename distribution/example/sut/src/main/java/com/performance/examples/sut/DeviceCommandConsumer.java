package com.performance.examples.sut;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Scenario 1: consumes Kafka commands, looks up the device address in the
 * database, then calls that address over HTTP.
 * <p>
 * Enabled by {@code sut.kafka-consumer.enabled=true} (default true).
 */
@Component
@ConditionalOnProperty(prefix = "sut.kafka-consumer", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DeviceCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandConsumer.class);

    private final DeviceRepository deviceRepository;
    private final DeviceCommandHttpClient httpClient;

    public DeviceCommandConsumer(DeviceRepository deviceRepository,
                                 DeviceCommandHttpClient httpClient) {
        this.deviceRepository = deviceRepository;
        this.httpClient = httpClient;
    }

    @KafkaListener(
            topics = "${sut.topics.commands:iot-commands}",
            groupId = "${spring.kafka.consumer.group-id:sut-consumer}",
            concurrency = "${sut.kafka-consumer.concurrency:3}")
    public void handle(String message) {
        try {
            String deviceId = extractDeviceId(message);
            if (deviceId == null) {
                log.warn("action=skip_message reason=no_device_id message={}", message);
                return;
            }

            Optional<String> address = deviceRepository.findAddressByDeviceId(deviceId);
            if (address.isEmpty()) {
                log.warn("action=skip_message reason=unknown_device device_id={}", deviceId);
                return;
            }

            int status = httpClient.sendCommand(address.get(), message);
            log.info("action=command_dispatched device_id={} address={} status={}",
                    deviceId, address.get(), status);

        } catch (Exception e) {
            log.error("action=dispatch_error message={}", message, e);
            // No rethrow: the message is acknowledged (no infinite Kafka retry).
        }
    }

    private String extractDeviceId(String json) {
        int idx = json.indexOf("\"device_id\"");
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1) + 1;
        int end = json.indexOf('"', start);
        return start > 0 && end > start ? json.substring(start, end) : null;
    }
}
