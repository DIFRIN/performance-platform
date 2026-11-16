package com.performance.platform.plugin;

/**
 * Interface optionnelle implementee par un {@link TaskExecutor} qui peut refuser
 * temporairement de nouvelles taches (ex: limite de concurrence atteinte).
 *
 * <p>Contrat :</p>
 * <ul>
 *   <li>{@link #canAcceptNewTask()} est non bloquant, sans effet de bord et thread-safe.</li>
 *   <li>Un {@code false} est transitoire : l'agent ne claim pas la tache, qui reste
 *       disponible pour un autre agent ou une tentative ulterieure.</li>
 *   <li>Un executor qui n'implemente pas cette interface est toujours disponible.</li>
 * </ul>
 *
 * <p>Ne modifie pas {@link TaskExecutor}. Reference : ADR-066.</p>
 */
public interface ExecutorAvailability {

    /**
     * @return {@code true} si l'executor peut accepter une nouvelle tache maintenant
     */
    boolean canAcceptNewTask();
}
