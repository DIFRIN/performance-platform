package com.performance.platform.injection.gatling.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.injection.gatling.worker.WorkerStates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWorkerStateTest {

    @Test
    void protocolMixSurvivesJsonRoundTripIntoWorkerHolders() throws Exception {
        var protocol = new HttpInjectionProtocol((com.performance.platform.plugin.resource.EndpointResolver) null);
        Map<String, Object> params = Map.of("baseUrl", "http://sut", "requests", List.of(
                Map.of("name", "create", "weight", 70, "path", "/events", "method", "POST", "body", "{\"a\":1}"),
                Map.of("name", "read", "weight", 30, "path", "/events/1")));
        String id = "step-rt";
        try (var prepared = protocol.prepare("exec", id, params)) {
            var json = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> state = json.readValue(json.writeValueAsString(prepared.workerState()), Map.class);
            HttpRequestSpecHolder.remove(id); // simule la JVM fille : holders vides

            WorkerStates.restore(id, state);

            var mix = HttpRequestSpecHolder.getMix(id);
            assertThat(mix.requests()).extracting(WeightedRequest::name).containsExactly("create", "read");
            assertThat(mix.normalizedWeight("create")).isEqualTo(0.7);
            assertThat(mix.requests().getFirst().spec().method()).isEqualTo("POST");
            assertThat(mix.requests().getFirst().spec().body()).isEqualTo("{\"a\":1}");
            assertThat(HttpRequestSpecHolder.get(id)).isNotNull();
        } finally {
            HttpRequestSpecHolder.remove(id);
        }
    }
}
