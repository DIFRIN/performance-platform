package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.plugin.TaskExecutor;

import java.util.Set;

/**
 * Registre unifie des {@link TaskExecutor} internes (Spring) et externes (plugins).
 *
 * <p>Resout un executor par couple {@code (phase, name)}. En cas de collision entre
 * un executor externe et un interne sur le meme couple, l'externe prime (override).
 * </p>
 *
 * <p>Le {@link #lookup(Phase, String)} leve une exception si aucun executor n'est
 * enregistre pour la combinaison demandee.</p>
 */
public interface PluginRegistry {

    /**
     * Resout un {@link TaskExecutor} pour le couple {@code (phase, name)}.
     *
     * @param phase la phase d'execution (non-null)
     * @param name  le nom de la tache (non-null, non-blank)
     * @return le {@link TaskExecutor} enregistre
     * @throws NullPointerException       si phase ou name est null
     * @throws IllegalArgumentException   si name est blank
     * @throws UnsupportedTaskNameException si aucun executor n'est enregistre pour ce couple
     */
    TaskExecutor lookup(Phase phase, String name);

    /**
     * Verifie si un executor est enregistre pour le couple {@code (phase, name)}.
     *
     * @param phase la phase d'execution (non-null)
     * @param name  le nom de la tache (non-null, non-blank)
     * @return true si un executor existe
     */
    boolean contains(Phase phase, String name);

    /**
     * Retourne l'ensemble des noms de taches enregistres pour une phase donnee.
     *
     * @param phase la phase d'execution (non-null)
     * @return l'ensemble des noms (jamais null, peut etre vide)
     */
    Set<String> namesFor(Phase phase);
}
