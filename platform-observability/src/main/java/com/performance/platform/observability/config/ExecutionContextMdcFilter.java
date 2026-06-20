package com.performance.platform.observability.config;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Pose et retire les cles MDC standard pour le logging structure JSON.
 * <p>
 * Le contrat est "set → log → clear" avec un bloc {@code try/finally}
 * pour garantir le nettoyage meme en cas d'exception :
 * <pre>{@code
 *   mdcFilter.setExecutionId(executionId);
 *   mdcFilter.setTaskId(taskId);
 *   try {
 *       log.info("action=execute ...");
 *   } finally {
 *       mdcFilter.clearAll();
 *   }
 * }</pre>
 * <p>
 * Les cles MDC exposees sont : {@code executionId}, {@code scenarioId},
 * {@code taskId}, {@code agentId}, {@code phase}.
 * <p>
 * <b>CNF-04:</b> Format de log conforme : action={} executionId={} taskId={}.
 * Les cles MDC sont automatiquement injectees dans le pattern JSON par
 * l'encodeur logback (voir {@code logback-spring.xml}).
 */
@Component
public class ExecutionContextMdcFilter {

    static final String MDC_EXECUTION_ID = "executionId";
    static final String MDC_SCENARIO_ID = "scenarioId";
    static final String MDC_TASK_ID = "taskId";
    static final String MDC_AGENT_ID = "agentId";
    static final String MDC_PHASE = "phase";

    static final String[] ALL_KEYS = {
            MDC_EXECUTION_ID, MDC_SCENARIO_ID, MDC_TASK_ID, MDC_AGENT_ID, MDC_PHASE
    };

    /**
     * Pose l'executionId dans le MDC.
     */
    public void setExecutionId(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        MDC.put(MDC_EXECUTION_ID, executionId.value());
    }

    /**
     * Pose le scenarioId dans le MDC.
     */
    public void setScenarioId(ScenarioId scenarioId) {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        MDC.put(MDC_SCENARIO_ID, scenarioId.value());
    }

    /**
     * Pose le taskId dans le MDC.
     */
    public void setTaskId(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        MDC.put(MDC_TASK_ID, taskId.value());
    }

    /**
     * Pose l'agentId dans le MDC.
     */
    public void setAgentId(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        MDC.put(MDC_AGENT_ID, agentId.value());
    }

    /**
     * Pose la phase dans le MDC.
     */
    public void setPhase(Phase phase) {
        Objects.requireNonNull(phase, "phase must not be null");
        MDC.put(MDC_PHASE, phase.name());
    }

    /**
     * Retire toutes les cles MDC gerees par ce filtre.
     * <p>
     * Appele depuis un bloc {@code finally} pour eviter les fuites
     * de contexte entre threads (Virtual Threads notamment).
     */
    public void clearAll() {
        for (String key : ALL_KEYS) {
            MDC.remove(key);
        }
    }

    /**
     * Retourne une snapshot immuable du contexte MDC courant.
     * Utile pour le debugging et les tests.
     */
    public Map<String, String> getContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context != null ? Map.copyOf(context) : Map.of();
    }
}
