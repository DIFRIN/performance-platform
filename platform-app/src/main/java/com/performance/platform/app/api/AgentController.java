package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.AgentResponse;
import com.performance.platform.application.ports.out.AgentRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for agent listing, active only in ORCHESTRATOR role.
 * <p>
 * Wires {@link AgentRegistryPort#findAll()} and maps each
 * {@link com.performance.platform.domain.agent.AgentDescriptor} to
 * {@link AgentResponse}.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "runtime", name = "role", havingValue = "ORCHESTRATOR")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentRegistryPort agentRegistry;

    public AgentController(AgentRegistryPort agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    /**
     * Returns all registered agents with their current state and capabilities.
     *
     * @return list of agent responses
     */
    @GetMapping("/agents")
    public List<AgentResponse> listAgents() {
        log.info("action=list_agents");
        return agentRegistry.findAll().stream()
                .map(agent -> new AgentResponse(
                        agent.id().value(),
                        agent.name(),
                        agent.state().name(),
                        agent.supportedTaskNames(),
                        agent.lastHeartbeatAt().toString()))
                .toList();
    }
}
