package com.performance.platform.engine.shared;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;

import java.util.List;

/**
 * Abstrait la strategie d'execution d'un step individuel.
 *
 * <p>Le moteur a un seul chemin d'execution (ADR-059) : il dispatche toujours
 * via les ports de transport, quel que soit le mode. En LOCAL le transport est
 * le transport en memoire et l'agent est embarque.</p>
 *
 * <p>Une <b>action step</b> est executee par {@link #dispatch}. Une
 * <b>service step</b> (ADR-054) est demarree par {@link #startService} et
 * arretee par {@link #stopService} ; son resultat est publie au STOP.</p>
 */
public interface StepDispatcher {

    /**
     * Execute une action step et retourne un resultat par agent accorde
     * (un seul en {@code fanOut: one}, tous les agents accordes en {@code all}).
     *
     * @param execStep le step a executer
     * @param context  le contexte d'execution courant
     * @return les resultats, jamais null
     */
    List<StepDispatchResult> dispatch(ExecutionStep execStep, ExecutionContext context);

    /**
     * Demarre une service step. Retourne quand un agent a ete accorde et a
     * demarre le service ; le service reste vivant jusqu'au STOP.
     *
     * @param execStep la service step
     * @param context  le contexte d'execution courant
     */
    void startService(ExecutionStep execStep, ExecutionContext context);

    /**
     * Arrete une service step et attend le resultat publie au STOP.
     *
     * @param execStep la service step
     * @param context  le contexte d'execution courant
     * @return les resultats, jamais null
     */
    List<StepDispatchResult> stopService(ExecutionStep execStep, ExecutionContext context);
}
