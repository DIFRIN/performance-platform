package com.performance.platform.transport.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.LifecycleAction;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.event.TaskGrantSignal;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.kafka.KafkaMessageCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSignalDecoderTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static Stream<AgentSignal> signals() {
        var execution = ExecutionId.generate();
        var task = TaskId.of("inject");
        var issuedAt = Instant.parse("2026-11-06T10:00:00Z");
        return Stream.of(
                new TaskGrantSignal(SignalId.generate(), execution, task, MessageId.generate(),
                        AgentId.of("agent-1"), true, issuedAt, new GrantShare(2, 4, 0.25)),
                new TaskGrantSignal(SignalId.generate(), execution, task, MessageId.generate(),
                        AgentId.of("agent-2"), true, issuedAt),
                new ScenarioRestartSignal(SignalId.generate(), execution, "RERUN", issuedAt),
                new ScenarioRestartSignal(SignalId.generate(), null, "ORCHESTRATOR_RESTART", issuedAt),
                new ExecutionLifecycleSignal(SignalId.generate(), execution, task, LifecycleAction.START,
                        Map.of("taskName", "gatling", "intervalSeconds", 2), issuedAt),
                new ExecutionLifecycleSignal(SignalId.generate(), execution, task, LifecycleAction.STOP,
                        Map.of("stopBehavior", "completeCurrentCycle"), issuedAt));
    }

    @ParameterizedTest
    @MethodSource("signals")
    void shouldRoundTripEverySignalOverKafka(AgentSignal signal) {
        var codec = new KafkaMessageCodec();
        assertThat(codec.decodeSignal(codec.encodeSignal(signal))).isEqualTo(signal);
    }

    @ParameterizedTest
    @MethodSource("signals")
    void shouldDecodeSocketJsonPayload(AgentSignal signal) throws Exception {
        var node = mapper.readTree(mapper.writeValueAsBytes(signal));
        assertThat(AgentSignalDecoder.decode(mapper, node)).isEqualTo(signal);
    }

    @ParameterizedTest
    @MethodSource("signals")
    void shouldDecodeAlreadyQueuedKafkaMessagesWithoutNewFields(AgentSignal signal) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode message = mapper.valueToTree(signal);
        message.put("@type", "SIGNAL");
        assertThat(new KafkaMessageCodec().decodeSignal(mapper.writeValueAsBytes(message))).isEqualTo(signal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"@type\":\"SIGNAL\",\"id\":{\"value\":\"s1\"},\"issuedAt\":\"2026-11-06T10:00:00Z\"}",
            "{\"@type\":\"SIGNAL\",\"id\":{\"value\":\"s1\"},\"issuedAt\":\"2026-11-06T10:00:00Z\",\"action\":\"START\"}",
            "{\"@type\":\"SIGNAL\",\"id\":{\"value\":\"s1\"},\"action\":\"INVALID\",\"taskId\":{\"value\":\"t1\"}}",
            "{\"@type\":\"SIGNAL\",\"action\":\"START\",\"reason\":\"ambiguous\"}",
            "{\"@type\":\"UNKNOWN\"}"
    })
    void shouldRejectInvalidSignalsRatherThanInventRestartReasons(String json) {
        var bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new KafkaMessageCodec().decodeSignal(bytes))
                .isInstanceOf(TransportException.class);
    }

    @org.junit.jupiter.api.Test
    void shouldDefaultToSingleShareWhenFieldAbsent() {
        var grant = new TaskGrantSignal(SignalId.generate(), ExecutionId.generate(), TaskId.of("t"),
                MessageId.generate(), AgentId.of("a"), true, Instant.parse("2026-11-06T10:00:00Z"),
                new GrantShare(2, 4, 0.25));
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.valueToTree(grant);
        node.remove("share");
        var decoded = (TaskGrantSignal) AgentSignalDecoder.decode(mapper, node);
        assertThat(decoded.share()).isEqualTo(GrantShare.single());
    }
}
