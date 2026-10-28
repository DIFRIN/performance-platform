package com.performance.platform.domain.template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTemplateTest {

    @Test
    void rendersKeyHeadersAndStringPayloadPerIndex() {
        var t = MessageTemplate.of("e1", "s1", Map.of(
                "key", "dev-{index:3}",
                "headers", Map.of("x-exec", "{executionId}", "x-n", "{index*10}"),
                "payload", "{\"id\":{index}}"));
        var r = t.render(7);
        assertThat(r.key()).isEqualTo("dev-007");
        assertThat(r.value()).isEqualTo("{\"id\":7}");
        assertThat(r.headers()).containsEntry("x-exec", "e1").containsEntry("x-n", "70");
    }

    @Test
    void serializesStructuredPayloadAsJson() {
        var t = MessageTemplate.of("e", "s", Map.of("payload",
                Map.of("id", "{index}", "tags", List.of("a\"b", 2), "ok", true)));
        assertThat(t.render(3).value()).contains("\"id\":\"3\"", "\"tags\":[\"a\\\"b\",2]", "\"ok\":true");
    }

    @Test
    void defaultsAndLegacyAlias() {
        var d = MessageTemplate.of("e", "s", Map.of()).render(2);
        assertThat(d.key()).isEqualTo("msg-2");
        assertThat(d.value()).isEqualTo("perf-message-2");
        assertThat(MessageTemplate.of("e", "s", Map.of("messageTemplate", "m-{index}")).render(2).value())
                .isEqualTo("m-2");
    }

    @Test
    void rejectsInvalidHeadersAndPlaceholders() {
        assertThatThrownBy(() -> MessageTemplate.of("e", "s", Map.of("headers", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageTemplate.of("e", "s", Map.of("key", "{random:9-1}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
