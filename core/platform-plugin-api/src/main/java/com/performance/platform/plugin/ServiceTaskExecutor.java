package com.performance.platform.plugin;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

/**
 * Contrat optionnel pour une etape de service (ADR-054).
 * <p>
 * A la difference d'une action step, une service step a une duree de vie :
 * {@link #executeService} demarre le service, reste bloque jusqu'au STOP
 * demande par l'orchestrateur via {@link ServiceControl}, puis retourne un
 * resultat publie au STOP.
 *
 * <p>Un {@link TaskExecutor} qui ne declare pas cette interface reste une
 * action step et n'est jamais invoque pour une etape avec {@code during}.</p>
 *
 * <p>0 annotation framework, interface Java pure.</p>
 */
public interface ServiceTaskExecutor extends TaskExecutor {

    /**
     * Execute le service jusqu'au STOP.
     *
     * @param context le contexte d'execution
     * @param step    la definition de la service step
     * @param control controle de duree de vie (STOP)
     * @return le resultat publie au STOP
     */
    TaskResult executeService(ExecutionContext context, StepDefinition step, ServiceControl control);
}
