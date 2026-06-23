package com.performance.platform.app.api;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AgentController")
class AgentControllerTest {

    private final AgentRegistryPort agentRegistry = mock(AgentRegistryPort.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new AgentController(agentRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /agents")
    class ListAgents {

        @Test
        @DisplayName("should return 200 with agent list when agents are registered")
        void shouldReturnAgentList() throws Exception {
            var now = Instant.parse("2026-06-23T12:00:00Z");
            var agent1 = new AgentDescriptor(
                    AgentId.of("agent-001"),
                    "worker-east",
                    "10.0.0.1",
                    9090,
                    null,
                    Set.of("gatling", "jmeter"),
                    new AgentCapabilities(4, "2.0.0"),
                    AgentState.IDLE,
                    now.minus(Duration.ofHours(1)),
                    now,
                    Duration.ofMinutes(5));
            var agent2 = new AgentDescriptor(
                    AgentId.of("agent-002"),
                    "worker-west",
                    "10.0.0.2",
                    9090,
                    null,
                    Set.of("gatling"),
                    new AgentCapabilities(2, "2.0.0"),
                    AgentState.EXECUTING,
                    now.minus(Duration.ofHours(2)),
                    now.minusSeconds(30),
                    Duration.ofMinutes(5));

            when(agentRegistry.findAll()).thenReturn(List.of(agent1, agent2));

            mockMvc.perform(get("/api/v1/agents")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].agentId").value("agent-001"))
                    .andExpect(jsonPath("$[0].name").value("worker-east"))
                    .andExpect(jsonPath("$[0].state").value("IDLE"))
                    .andExpect(jsonPath("$[0].supportedTasks.length()").value(2))
                    .andExpect(jsonPath("$[0].supportedTasks", hasItem("gatling")))
                    .andExpect(jsonPath("$[0].supportedTasks", hasItem("jmeter")))
                    .andExpect(jsonPath("$[0].lastHeartbeatAt").value("2026-06-23T12:00:00Z"))
                    .andExpect(jsonPath("$[1].agentId").value("agent-002"))
                    .andExpect(jsonPath("$[1].name").value("worker-west"))
                    .andExpect(jsonPath("$[1].state").value("EXECUTING"))
                    .andExpect(jsonPath("$[1].supportedTasks.length()").value(1))
                    .andExpect(jsonPath("$[1].supportedTasks", hasItem("gatling")))
                    .andExpect(jsonPath("$[1].lastHeartbeatAt").value("2026-06-23T11:59:30Z"));
        }

        @Test
        @DisplayName("should return 200 with empty list when no agents are registered")
        void shouldReturnEmptyList() throws Exception {
            when(agentRegistry.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/agents")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return agents in all valid states")
        void shouldReturnAgentsInAllStates() throws Exception {
            var now = Instant.parse("2026-06-23T12:00:00Z");
            var agent = new AgentDescriptor(
                    AgentId.of("agent-003"),
                    "draining-worker",
                    "10.0.0.3",
                    9090,
                    null,
                    Set.of("assertion"),
                    new AgentCapabilities(1, "2.0.0"),
                    AgentState.DRAINING,
                    now.minus(Duration.ofHours(3)),
                    now.minus(Duration.ofMinutes(10)),
                    Duration.ofMinutes(5));

            when(agentRegistry.findAll()).thenReturn(List.of(agent));

            mockMvc.perform(get("/api/v1/agents")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].state").value("DRAINING"));
        }
    }
}
