package com.performance.platform.transport.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.event.TaskGrantSignal;

/**
 * Decodes the signal wire format without requiring a new discriminator.
 * The three signal kinds are disjoint by their required fields.
 */
public final class AgentSignalDecoder {

    private AgentSignalDecoder() {
    }

    public static AgentSignal decode(ObjectMapper mapper, Object payload) {
        JsonNode node = mapper.valueToTree(payload);
        boolean grant = node.has("messageId") && node.has("granted") && node.has("agentId");
        boolean lifecycle = node.has("action") || node.has("taskId");
        if (grant && lifecycle && node.has("action")) {
            throw new IllegalArgumentException("Ambiguous signal: grant fields and lifecycle action");
        }
        if (grant) {
            // Champ share absent (ancien emetteur) => GrantShare.single() (ADR-067)
            if (!node.hasNonNull("share") && node instanceof ObjectNode obj) {
                obj.set("share", mapper.valueToTree(GrantShare.single()));
                return mapper.convertValue(obj, TaskGrantSignal.class);
            }
            return mapper.convertValue(payload, TaskGrantSignal.class);
        }
        if (lifecycle && node.has("reason")) {
            throw new IllegalArgumentException("Ambiguous signal: lifecycle fields and restart reason");
        }
        if (lifecycle) {
            return mapper.convertValue(payload, ExecutionLifecycleSignal.class);
        }
        return mapper.convertValue(payload, ScenarioRestartSignal.class);
    }
}
