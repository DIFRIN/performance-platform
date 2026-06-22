package com.performance.examples.device;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DeviceEventControllerTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceEventProducer deviceEventProducer;

    private DeviceEventController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceEventController(deviceRepository, deviceEventProducer);
    }

    @Test
    void shouldReturn200WhenDeviceIsKnown() {
        when(deviceRepository.existsByDeviceId("device-0001")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.submitEvent("device-0001");

        assert response.getStatusCode() == HttpStatus.OK;
        Map<String, Object> body = response.getBody();
        assert body != null;
        assert "device-0001".equals(body.get("device_id"));
        assert Boolean.TRUE.equals(body.get("known"));
        assert Boolean.TRUE.equals(body.get("event_published"));

        verify(deviceEventProducer).publish("device-0001", true);
    }

    @Test
    void shouldReturn200WhenDeviceIsUnknown() {
        when(deviceRepository.existsByDeviceId("unknown-999")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.submitEvent("unknown-999");

        assert response.getStatusCode() == HttpStatus.OK;
        Map<String, Object> body = response.getBody();
        assert body != null;
        assert "unknown-999".equals(body.get("device_id"));
        assert Boolean.FALSE.equals(body.get("known"));
        assert Boolean.TRUE.equals(body.get("event_published"));

        verify(deviceEventProducer).publish("unknown-999", false);
    }

    @Test
    void shouldReturn400WhenDeviceIdMissing() {
        ResponseEntity<Map<String, Object>> response = controller.submitEvent(null);

        assert response.getStatusCode() == HttpStatus.BAD_REQUEST;
        Map<String, Object> body = response.getBody();
        assert body != null;
        assert "device_id is required".equals(body.get("error"));

        verify(deviceEventProducer, never()).publish(anyString(), anyBoolean());
        verify(deviceRepository, never()).existsByDeviceId(anyString());
    }

    @Test
    void shouldReturn400WhenDeviceIdBlank() {
        ResponseEntity<Map<String, Object>> response = controller.submitEvent("   ");

        assert response.getStatusCode() == HttpStatus.BAD_REQUEST;
        Map<String, Object> body = response.getBody();
        assert body != null;
        assert "device_id is required".equals(body.get("error"));

        verify(deviceEventProducer, never()).publish(anyString(), anyBoolean());
        verify(deviceRepository, never()).existsByDeviceId(anyString());
    }

    @Test
    void shouldReturnHealthUp() {
        ResponseEntity<Map<String, String>> response = controller.health();

        assert response.getStatusCode() == HttpStatus.OK;
        Map<String, String> body = response.getBody();
        assert body != null;
        assert "UP".equals(body.get("status"));
    }

    @Test
    void shouldPublishEventEvenWhenDeviceUnknown() {
        when(deviceRepository.existsByDeviceId("device-0500")).thenReturn(false);

        controller.submitEvent("device-0500");

        // Kafka publish is always called (fire-and-forget), even for unknown devices
        verify(deviceEventProducer).publish("device-0500", false);
    }
}
