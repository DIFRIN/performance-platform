package com.performance.platform.transport.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import java.util.Map;

/**
 * Codec JSON pour la (de)serialisation des messages transportes via Kafka.
 * <p>
 * Les messages destines au topic events (qui porte a la fois des
 * {@link ExecutionEvent} et des {@link AgentLifecycleEvent}) sont enveloppes
 * avec un champ {@code @type} pour le dispatching a la reception.
 * <p>
 * Thread-safe : utilise un {@link ObjectMapper} immutable partage.
 */
public final class KafkaMessageCodec {

    static final String TYPE_TASK_REQUEST = "TASK_REQUEST";
    static final String TYPE_EXECUTION_EVENT = "EXECUTION_EVENT";
    static final String TYPE_AGENT_LIFECYCLE_EVENT = "AGENT_LIFECYCLE_EVENT";
    static final String TYPE_SIGNAL = "SIGNAL";
    private static final String TYPE_FIELD = "@type";

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper mapper;

    public KafkaMessageCodec() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules();
    }

    // === Tasks topic (single type, no envelope) ===

    /**
     * Serialise une {@link TaskExecutionRequest} en JSON.
     */
    public byte[] encodeTaskRequest(TaskExecutionRequest request) {
        try {
            return mapper.writeValueAsBytes(request);
        } catch (Exception e) {
            throw new TransportException("Failed to encode task request: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialise des bytes JSON en {@link TaskExecutionRequest}.
     */
    public TaskExecutionRequest decodeTaskRequest(byte[] data) {
        try {
            return mapper.readValue(data, TaskExecutionRequest.class);
        } catch (Exception e) {
            throw new TransportException("Failed to decode task request: " + e.getMessage(), e);
        }
    }

    // === Events topic (two types, discriminated by @type field) ===

    /**
     * Serialise un {@link ExecutionEvent} avec un marqueur {@code @type}
     * pour le dispatching cote consumer.
     */
    public byte[] encodeExecutionEvent(ExecutionEvent event) {
        try {
            Map<String, Object> map = mapper.convertValue(event, MAP_TYPE);
            map.put(TYPE_FIELD, TYPE_EXECUTION_EVENT);
            return mapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new TransportException("Failed to encode execution event: " + e.getMessage(), e);
        }
    }

    /**
     * Serialise un {@link AgentLifecycleEvent} avec un marqueur {@code @type}
     * pour le dispatching cote consumer.
     */
    public byte[] encodeAgentLifecycleEvent(AgentLifecycleEvent event) {
        try {
            Map<String, Object> map = mapper.convertValue(event, MAP_TYPE);
            map.put(TYPE_FIELD, TYPE_AGENT_LIFECYCLE_EVENT);
            return mapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new TransportException("Failed to encode agent lifecycle event: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialise un message du topic events et le dispatche vers
     * {@link ExecutionEvent} ou {@link AgentLifecycleEvent} selon
     * le marqueur {@code @type}.
     *
     * @return une instance de {@link ExecutionEvent} ou {@link AgentLifecycleEvent}
     */
    public Object decodeEvent(byte[] data) {
        try {
            Map<String, Object> map = mapper.readValue(data, MAP_TYPE);
            String type = (String) map.remove(TYPE_FIELD);
            if (type == null) {
                throw new TransportException("Missing " + TYPE_FIELD + " field in event message");
            }
            return switch (type) {
                case TYPE_EXECUTION_EVENT ->
                        mapper.convertValue(map, ExecutionEvent.class);
                case TYPE_AGENT_LIFECYCLE_EVENT ->
                        mapper.convertValue(map, AgentLifecycleEvent.class);
                default ->
                        throw new TransportException("Unknown event type: " + type);
            };
        } catch (TransportException e) {
            throw e;
        } catch (Exception e) {
            throw new TransportException("Failed to decode event: " + e.getMessage(), e);
        }
    }

    // === Signals topic (sealed interface, discriminated by @type) ===

    /**
     * Serialise un {@link AgentSignal} avec un marqueur {@code @type}
     * pour le dispatching cote consumer.
     */
    public byte[] encodeSignal(AgentSignal signal) {
        try {
            Map<String, Object> map = mapper.convertValue(signal, MAP_TYPE);
            map.put(TYPE_FIELD, TYPE_SIGNAL);
            return mapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new TransportException("Failed to encode signal: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialise un message du topic signals en {@link AgentSignal}.
     */
    public AgentSignal decodeSignal(byte[] data) {
        try {
            Map<String, Object> map = mapper.readValue(data, MAP_TYPE);
            String type = (String) map.remove(TYPE_FIELD);
            if (!TYPE_SIGNAL.equals(type)) {
                throw new TransportException("Unexpected signal type: " + type);
            }
            // Currently the only permitted subtype is ScenarioRestartSignal
            return mapper.convertValue(map, ScenarioRestartSignal.class);
        } catch (TransportException e) {
            throw e;
        } catch (Exception e) {
            throw new TransportException("Failed to decode signal: " + e.getMessage(), e);
        }
    }
}
