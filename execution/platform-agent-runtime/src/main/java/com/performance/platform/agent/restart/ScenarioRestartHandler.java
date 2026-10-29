package com.performance.platform.agent.restart;

import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.plugin.StatefulResourceCleaner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gère la réception d'un {@link ScenarioRestartSignal} côté agent.
 * <p>
 * Flux de traitement :
 * <ol>
 *   <li>Appel {@link StatefulResourceCleaner#cleanup} sur chaque cleaner enregistré</li>
 *   <li>Annulation des tâches actives correspondant à l'executionId (ou toutes si null)</li>
 *   <li>Transition de l'état agent vers IDLE si plus aucune tâche active</li>
 * </ol>
 * <p>
 * Les erreurs d'un cleaner n'empêchent pas l'exécution des autres cleaners
 * ni l'annulation des tâches (best-effort).
 */
public class ScenarioRestartHandler {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRestartHandler.class);

    private final List<StatefulResourceCleaner> cleaners;

    /**
     * Construit un handler de restart avec la liste des cleaners disponibles.
     *
     * @param cleaners la liste des cleaners (peut être vide — le restart annule quand même les tâches)
     */
    public ScenarioRestartHandler(List<StatefulResourceCleaner> cleaners) {
        this.cleaners = List.copyOf(cleaners);
    }

    public int cleanerCount() {
        return cleaners.size();
    }

    /**
     * Traite un signal de restart.
     * <p>
     * Ordre : cleanup des ressources stateful → annulation des tâches → transition IDLE.
     *
     * @param signal           le signal de restart reçu
     * @param activeTasks      la map des tâches actives (MessageId → Future)
     * @param activeTaskCount  le compteur atomique de tâches actives
     * @param currentState     la référence atomique de l'état courant de l'agent
     * @param agentId          l'identifiant de l'agent (pour le logging)
     */
    public void onSignal(ScenarioRestartSignal signal,
                         Map<MessageId, Future<?>> activeTasks,
                         AtomicInteger activeTaskCount,
                         AtomicReference<AgentState> currentState,
                         AgentId agentId) {

        var executionId = signal.executionId();
        var allCleanup = executionId == null;

        log.info("action=scenario_restart_handler agentId={} executionId={} reason={} cleanerCount={}",
                agentId.value(),
                allCleanup ? "ALL" : executionId.value(),
                signal.reason(),
                cleaners.size());

        // 1. Cleanup des ressources stateful (best-effort par cleaner)
        for (var cleaner : cleaners) {
            try {
                cleaner.cleanup(executionId);
                log.debug("action=cleaner_cleanup agentId={} cleaner={} executionId={}",
                        agentId.value(), cleaner.getClass().getSimpleName(),
                        allCleanup ? "ALL" : executionId.value());
            } catch (Exception e) {
                log.warn("action=cleaner_cleanup_failed agentId={} cleaner={} executionId={}",
                        agentId.value(), cleaner.getClass().getSimpleName(),
                        allCleanup ? "ALL" : executionId.value(), e);
            }
        }

        // 2. Annuler les tâches correspondant à l'executionId (ou toutes si null)
        var toCancel = new ArrayList<Map.Entry<MessageId, Future<?>>>();
        for (var entry : activeTasks.entrySet()) {
            if (allCleanup) {
                toCancel.add(entry);
            }
            // Note: sans mapping MessageId → ExecutionId, un restart ciblé
            // annule quand même toutes les tâches (comportement conservateur).
            // L'optimisation nécessiterait de conserver le mapping (ISSUE future).
        }

        // Si executionId est spécifique et qu'on n'a pas de mapping, on annule tout aussi
        // (match implicite dans le conservatisme)
        if (!allCleanup && toCancel.isEmpty()) {
            toCancel.addAll(activeTasks.entrySet());
        }

        for (var entry : toCancel) {
            log.info("action=cancel_task agentId={} messageId={} reason=scenario_restart",
                    agentId.value(), entry.getKey().value());
            entry.getValue().cancel(true);
            // Remove atomically — si déjà retiré par executeTask, ne pas double-décrémenter
            if (activeTasks.remove(entry.getKey()) != null) {
                activeTaskCount.decrementAndGet();
            }
        }

        // 3. Transition IDLE si plus aucune tâche active
        if (activeTaskCount.get() == 0) {
            currentState.compareAndSet(AgentState.EXECUTING, AgentState.IDLE);
            currentState.compareAndSet(AgentState.DRAINING, AgentState.IDLE);
        }

        log.info("action=scenario_restart_handler_complete agentId={} cancelledTasks={} remainingTasks={}",
                agentId.value(), toCancel.size(), activeTaskCount.get());
    }
}
