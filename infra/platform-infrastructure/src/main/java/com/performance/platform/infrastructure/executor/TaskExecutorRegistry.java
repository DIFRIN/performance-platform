package com.performance.platform.infrastructure.executor;

import com.performance.platform.plugin.TaskExecutor;

import java.util.Set;

/**
 * Registre central des {@link TaskExecutor} disponibles, resolution par {@code taskName} (String).
 * Remplace l'ancien registre base sur un enum {@code TaskType}.
 *
 * <p>Les implementations Spring collectent automatiquement tous les beans
 * {@link TaskExecutor} et les enregistrent par leur {@link TaskExecutor#getSupportedTaskName()}.</p>
 *
 * <p>Jamais de if/switch sur un type. La cle de registre est le nom declare
 * par l'executor via {@code getSupportedTaskName()}.</p>
 */
public interface TaskExecutorRegistry {

    /**
     * Enregistre un nouvel executor. Si un executor avec le meme {@code taskName}
     * existe deja, le nouvel executor remplace l'ancien (dernier enregistre gagne).
     *
     * @param executor l'executor a enregistrer (non-null)
     * @throws NullPointerException si executor est null
     */
    void register(TaskExecutor executor);

    /**
     * Resout un executor par son nom de tache.
     *
     * @param taskName le nom de tache declare par l'executor
     * @return l'executor correspondant
     * @throws UnsupportedTaskNameException si aucun executor n'est enregistre pour ce taskName
     */
    TaskExecutor getFor(String taskName) throws UnsupportedTaskNameException;

    /**
     * Retourne l'ensemble des noms de taches supportes par les executors enregistres.
     *
     * @return un ensemble immuable des taskNames (potentiellement vide)
     */
    Set<String> getSupportedTaskNames();
}
