package com.performance.platform.transport.rabbitmq;

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
 * Codec JSON pour la (de)serialisation des messages transportes via RabbitMQ.
 * <p>
 * Tous les messages sont enveloppes avec un champ {@code @type} pour le
 * dispatching a la reception sur la file unique. Les types possibles :
 * {@code TASK_REQUEST}, {@code EXECUTION_EVENT},
 * {@code AGENT_LIFECYCLE_EVENT}, {@code SIGNAL}.
 * <p>
 * Thread-safe : utilise un {@link ObjectMapper} immutable partage.
 */
public final class RabbitMQMessageCodec {

    static final String TYPE_TASK_REQUEST = "TASK_REQUEST";
    static final String TYPE_EXECUTION_EVENT = "EXECUTION_EVENT";
    static final String TYPE_AGENT_LIFECYCLE_EVENT = "AGENT_LIFECYCLE_EVENT";
    static final String TYPE_SIGNAL = "SIGNAL";
    private static final String TYPE_FIELD = "@type";

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper mapper;

    public RabbitMQMessageCodec() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules();
    }

    // === encode (all types include @type envelope for single-queue dispatching) ===

    /**
     * Serialise une {@link TaskExecutionRequest} en JSON avec marqueur {@code @type}.
     */
    public byte[] encodeTaskRequest(TaskExecutionRequest request) {
        try {
            Map<String, Object> map = mapper.convertValue(request, MAP_TYPE);
            map.put(TYPE_FIELD, TYPE_TASK_REQUEST);
            return mapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new TransportException("Failed to encode task request: " + e.getMessage(), e);
        }
    }

    /**
     * Serialise un {@link ExecutionEvent} en JSON avec marqueur {@code @type}.
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
     * Serialise un {@link AgentLifecycleEvent} en JSON avec marqueur {@code @type}.
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
     * Serialise un {@link AgentSignal} en JSON avec marqueur {@code @type}.
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

    // === decode (single entry point, dispatched by @type) ===

    /**
     * Deserialise un message de la file unique et le dispatche vers le
     * type approprie base sur le marqueur {@code @type}.
     *
     * @return une instance de {@link TaskExecutionRequest}, {@link ExecutionEvent},
     *         {@link AgentLifecycleEvent} ou {@link AgentSignal}
     */
    public Object decodeMessage(byte[] data) {
        try {
            Map<String, Object> map = mapper.readValue(data, MAP_TYPE);
            String type = (String) map.remove(TYPE_FIELD);
            if (type == null) {
                throw new TransportException("Missing " + TYPE_FIELD + " field in message");
            }
            return switch (type) {
                case TYPE_TASK_REQUEST ->
                        mapper.convertValue(map, TaskExecutionRequest.class);
                case TYPE_EXECUTION_EVENT ->
                        mapper.convertValue(map, ExecutionEvent.class);
                case TYPE_AGENT_LIFECYCLE_EVENT ->
                        mapper.convertValue(map, AgentLifecycleEvent.class);
                case TYPE_SIGNAL ->
                        mapper.convertValue(map, ScenarioRestartSignal.class);
                default ->
                        throw new TransportException("Unknown message type: " + type);
            };
        } catch (TransportException e) {
            throw e;
        } catch (Exception e) {
            throw new TransportException("Failed to decode message: " + e.getMessage(), e);
        }
    }
}
