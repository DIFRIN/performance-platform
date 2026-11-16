package com.performance.platform.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Client HTTP minimal, sans dependance framework. */
public final class Http {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    public static JsonNode getJson(String url, Map<String, String> headers, Duration timeout) {
        return send(request(url, headers, timeout).GET().build(), url);
    }

    public static JsonNode postJson(String url, String body, Map<String, String> headers,
                                    Duration timeout) {
        HttpRequest.Builder builder = request(url, headers, timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        return send(builder.build(), url);
    }

    private static HttpRequest.Builder request(String url, Map<String, String> headers,
                                               Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout == null ? Duration.ofSeconds(30) : timeout)
                .header("Accept", "application/json");
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder;
    }

    private static JsonNode send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ProbeException(status == 401 || status == 403 ? 2 : 3,
                        "HTTP " + status + " on " + request.method() + " " + url
                                + ": " + truncate(response.body()));
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return MAPPER.nullNode();
            }
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw ProbeException.remote("Request failed: " + request.method() + " " + url
                    + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ProbeException.remote("Request interrupted: " + url, e);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 497) + "...";
    }
}
