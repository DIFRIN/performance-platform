package com.performance.platform.assertion.httpmock;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireMockQueryCriteriaTest {

    @Test
    void emptyWhenAbsent() {
        assertThat(WireMockQueryCriteria.build(null, "e", "s")).isEqualTo("{}");
        assertThat(WireMockQueryCriteria.build(Map.of(), "e", "s")).isEqualTo("{}");
    }

    @Test
    void staticValueUsesEqualToAndSubstitutesIds() {
        var params = new LinkedHashMap<String, Object>();
        params.put("device_id", "device-0001");
        params.put("run", "{executionId}");

        assertThat(WireMockQueryCriteria.build(params, "exec-1", "s"))
                .isEqualTo("{\"queryParameters\":{\"device_id\":{\"equalTo\":\"device-0001\"},"
                        + "\"run\":{\"equalTo\":\"exec-1\"}}}");
    }

    @Test
    void dynamicValueUsesRegex() {
        String json = WireMockQueryCriteria.build(Map.of("device_id", "device-{index}"), "e", "s");

        assertThat(json).contains("\"matches\"").contains("\\\\Qdevice-\\\\E\\\\d+");
    }

    @Test
    void richPlaceholdersUseRegex() {
        String json = WireMockQueryCriteria.build(
                Map.of("k", "{choice:a|b}-{random:1-9}"), "e", "s");

        assertThat(json).contains("\"matches\"");
    }

    @Test
    void rejectsNonMap() {
        assertThatThrownBy(() -> WireMockQueryCriteria.build("x=1", "e", "s"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
