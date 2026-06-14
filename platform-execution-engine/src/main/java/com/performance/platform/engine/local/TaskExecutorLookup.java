package com.performance.platform.engine.local;

import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;

/**
 * Resout un nom de tache ou d'assertion vers son executeur.
 * Abstraction locale du module engine, qui sera bridgee vers
 * {@code TaskExecutorRegistry} lorsque PDR-010 sera implemente.
 *
 * <p>0 annotation framework — interface Java pure.</p>
 */
public interface TaskExecutorLookup {

    /**
     * Trouve le {@link TaskExecutor} pour un nom de tache donne.
     *
     * @param taskName le nom de la tache (PREPARATION ou INJECTION)
     * @return l'executeur, ou null si non trouve
     */
    TaskExecutor findTaskExecutor(String taskName);

    /**
     * Trouve l'{@link AssertionExecutor} pour un nom d'assertion donne.
     *
     * @param assertionName le nom de l'assertion (ASSERTION)
     * @return l'executeur d'assertion, ou null si non trouve
     */
    AssertionExecutor findAssertionExecutor(String assertionName);
}
