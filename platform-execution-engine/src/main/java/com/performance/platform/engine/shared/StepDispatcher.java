package com.performance.platform.engine.shared;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;

/**
 * Abstrait la stratégie d'exécution d'un step individuel.
 *
 * <p>En LOCAL ({@code LocalStepDispatcher}) : appel direct à l'executor
 * dans la même JVM (mémoire partagée, pas de sérialisation).</p>
 *
 * <p>En DISTRIBUTED ({@code RemoteStepDispatcher}) : dispatch via
 * {@code ExecutionTransport} et attente des résultats via
 * {@code TaskCorrelationTracker}.</p>
 *
 * <p>Le {@link DagPhaseExecutor} utilise cette abstraction pour le
 * parcours DAG sans connaître la stratégie de dispatch sous-jacente.</p>
 */
@FunctionalInterface
public interface StepDispatcher {

    /**
     * Exécute un step et retourne son résultat.
     * L'implémentation est responsable de la résolution du
     * {@code TaskExecutor}, du retry, et du transport éventuel.
     *
     * @param execStep le step à exécuter (contient step + dagLevel + deps)
     * @param context  le contexte d'exécution courant
     * @param phase    la phase en cours (PREPARATION / INJECTION / ASSERTION)
     * @return le résultat de l'exécution du step
     */
    TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase);
}
