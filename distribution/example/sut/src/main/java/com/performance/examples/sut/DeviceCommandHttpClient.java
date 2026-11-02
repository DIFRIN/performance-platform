package com.performance.examples.sut;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the device endpoint address read from the database.
 * <p>
 * The example stores {@code host:port} (the embedded WireMock endpoint) in
 * {@code devices.device_dns}. A value that already contains a scheme is used
 * as-is; otherwise {@code http://} is prepended.
 */
@Component
public class DeviceCommandHttpClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandHttpClient.class);

    private final RestClient restClient;
    private final String commandPath;

    public DeviceCommandHttpClient(
            @Value("${sut.http.command-path:/command}") String commandPath) {
        this.commandPath = commandPath;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Sends the command payload to {@code <deviceAddress><commandPath>}.
     *
     * @return the HTTP status code, or {@code -1} when the call failed
     */
    public int sendCommand(String deviceAddress, String payload) {
        String baseUrl = deviceAddress.contains("://")
                ? deviceAddress
                : "http://" + deviceAddress;
        String url = baseUrl.replaceAll("/+$", "") + commandPath;
        try {
            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().value();
        } catch (Exception e) {
            log.error("action=send_command_failed address={} url={}", deviceAddress, url, e);
            return -1;
        }
    }
}
