package com.performance.examples.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeviceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${device.topics.events:device-events}")
    private String eventsTopic;

    public DeviceEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie un evenement device dans Kafka.
     * Message : {"device_id":"xxx","known":true,"timestamp":1234567890}
     */
    public void publish(String deviceId, boolean known) {
        String message = String.format(
                "{\"device_id\":\"%s\",\"known\":%b,\"timestamp\":%d}",
                deviceId, known, System.currentTimeMillis());
        kafkaTemplate.send(eventsTopic, deviceId, message);
        log.debug("action=event_published device_id={} topic={}", deviceId, eventsTopic);
    }
}
