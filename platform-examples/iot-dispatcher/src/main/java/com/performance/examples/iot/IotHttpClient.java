package com.performance.examples.iot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IotHttpClient {

    private static final Logger log = LoggerFactory.getLogger(IotHttpClient.class);

    private final RestClient restClient;

    public IotHttpClient(@Value("${iot.http.device-gateway-url:http://wiremock:8080}") String gatewayUrl,
                         RestClient.Builder builder) {
        this.restClient = builder.baseUrl(gatewayUrl).build();
    }

    /**
     * Sends an HTTP POST command to the device (via gateway/WireMock).
     * Returns the HTTP status code of the response.
     */
    public int sendCommand(String deviceDns, String payload) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/command")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().value();
        } catch (Exception e) {
            log.error("action=send_command_failed dns={}", deviceDns, e);
            return -1;
        }
    }
}
