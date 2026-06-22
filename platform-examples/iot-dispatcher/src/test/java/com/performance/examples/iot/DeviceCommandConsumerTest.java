package com.performance.examples.iot;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceCommandConsumerTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private IotHttpClient iotHttpClient;

    private DeviceCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeviceCommandConsumer(deviceRepository, iotHttpClient);
    }

    @Test
    void shouldDispatchCommandWhenDeviceKnown() {
        String message = "{\"device_id\":\"dev-001\",\"command\":\"ping\"}";
        when(deviceRepository.findDnsByDeviceId("dev-001"))
                .thenReturn(Optional.of("device1.local"));
        when(iotHttpClient.sendCommand(eq("device1.local"), eq(message)))
                .thenReturn(200);

        consumer.handle(message);

        verify(iotHttpClient).sendCommand("device1.local", message);
    }

    @Test
    void shouldSkipMessageWhenDeviceUnknown() {
        String message = "{\"device_id\":\"dev-999\",\"command\":\"reboot\"}";
        when(deviceRepository.findDnsByDeviceId("dev-999"))
                .thenReturn(Optional.empty());

        consumer.handle(message);

        verify(iotHttpClient, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void shouldSkipMessageWhenNoDeviceId() {
        String message = "{\"command\":\"ping\"}";

        consumer.handle(message);

        verify(deviceRepository, never()).findDnsByDeviceId(anyString());
        verify(iotHttpClient, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void shouldCatchExceptionAndNotPropagate() {
        String message = "{\"device_id\":\"dev-002\",\"command\":\"ping\"}";
        when(deviceRepository.findDnsByDeviceId("dev-002"))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should not throw — exception is caught internally
        consumer.handle(message);

        verify(iotHttpClient, never()).sendCommand(anyString(), anyString());
    }
}
