package com.performance.platform.plugin;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

/**
 * Contrat unique pour les plugins de preparation et d'injection (interne ET externe).
 * Toute implementation de tache doit implementer cette interface et etre annotee
 * avec {@code @Preparation} ou {@code @Injection}.
 *
 * <p>Echec metier : retourner {@link TaskResult#failed}.
 * Exception uniquement pour erreur technique (catch par l'engine).</p>
 *
 * <p>0 annotation framework — interface Java pure.</p>
 *
 * @see com.performance.platform.plugin.Preparation
 * @see com.performance.platform.plugin.Injection
 */
public interface TaskExecutor {

    /**
     * Execute la tache avec le contexte d'execution et la definition d'etape.
     * Ne jamais lancer d'exception metier — utiliser {@link TaskResult#failed}.
     *
     * @param context le contexte d'execution immuable (cote orchestrateur)
     * @param step    la definition de l'etape a executer
     * @return le resultat de l'execution
     */
    TaskResult execute(ExecutionContext context, StepDefinition step);

    /**
     * Nom de la tache supportee. Doit correspondre au {@code name()} de
     * l'annotation {@code @Preparation} ou {@code @Injection}.
     * Remplace l'ancien {@code getSupportedType()} base sur un enum.
     *
     * @return le nom de tache (jamais null)
     */
    String getSupportedTaskName();
}
