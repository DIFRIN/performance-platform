package com.performance.platform.transport.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;

/**
 * Decodes the existing signal wire format without requiring a new discriminator.
 */
public final class AgentSignalDecoder {

    private AgentSignalDecoder() {
    }

    public static AgentSignal decode(ObjectMapper mapper, Object payload) {
        JsonNode node = mapper.valueToTree(payload);
        boolean lifecycle = node.has("action") || node.has("taskId");
        if (lifecycle && node.has("reason")) {
            throw new IllegalArgumentException("Ambiguous signal: lifecycle fields and restart reason");
        }
        if (lifecycle) {
            return mapper.convertValue(payload, ExecutionLifecycleSignal.class);
        }
        return mapper.convertValue(payload, ScenarioRestartSignal.class);
    }
}
