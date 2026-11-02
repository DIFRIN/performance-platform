package com.performance.examples.sut;

import static org.mockito.ArgumentMatchers.anyString;
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
    private DeviceCommandHttpClient httpClient;

    private DeviceCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeviceCommandConsumer(deviceRepository, httpClient);
    }

    @Test
    void shouldDispatchCommandToAddressReadFromDatabase() {
        String message = "{\"device_id\":\"dev-001\",\"command\":\"ping\"}";
        when(deviceRepository.findAddressByDeviceId("dev-001"))
                .thenReturn(Optional.of("172.28.0.12:8090"));
        when(httpClient.sendCommand("172.28.0.12:8090", message)).thenReturn(200);

        consumer.handle(message);

        verify(httpClient).sendCommand("172.28.0.12:8090", message);
    }

    @Test
    void shouldSkipMessageWhenDeviceUnknown() {
        String message = "{\"device_id\":\"dev-999\",\"command\":\"reboot\"}";
        when(deviceRepository.findAddressByDeviceId("dev-999"))
                .thenReturn(Optional.empty());

        consumer.handle(message);

        verify(httpClient, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void shouldSkipMessageWhenNoDeviceId() {
        consumer.handle("{\"command\":\"ping\"}");

        verify(deviceRepository, never()).findAddressByDeviceId(anyString());
        verify(httpClient, never()).sendCommand(anyString(), anyString());
    }

    @Test
    void shouldCatchExceptionAndNotPropagate() {
        String message = "{\"device_id\":\"dev-002\",\"command\":\"ping\"}";
        when(deviceRepository.findAddressByDeviceId("dev-002"))
                .thenThrow(new RuntimeException("DB connection lost"));

        consumer.handle(message);

        verify(httpClient, never()).sendCommand(anyString(), anyString());
    }
}
