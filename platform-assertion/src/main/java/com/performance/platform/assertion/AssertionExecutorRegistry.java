package com.performance.platform.assertion;

import com.performance.platform.plugin.AssertionExecutor;

import java.util.Set;

/**
 * Registre central des {@link AssertionExecutor} disponibles, resolution
 * par {@code assertionName} (String). Miroir du {@code TaskExecutorRegistry}.
 * <p>
 * Les implementations Spring collectent automatiquement tous les beans
 * {@link AssertionExecutor} et les enregistrent par leur
 * {@link AssertionExecutor#getSupportedAssertionName()}.
 * <p>
 * Jamais de if/switch sur un type. La cle de registre est le nom declare
 * par l'executor via {@code getSupportedAssertionName()}.
 */
public interface AssertionExecutorRegistry {

    /**
     * Enregistre un nouvel executor d'assertion. Si un executor avec le meme
     * {@code assertionName} existe deja, le nouvel executor remplace l'ancien.
     *
     * @param executor l'executor a enregistrer (non-null)
     * @throws NullPointerException si executor est null
     */
    void register(AssertionExecutor executor);

    /**
     * Resout un executor d'assertion par son nom.
     *
     * @param assertionName le nom declare par l'executor
     * @return l'executor correspondant
     * @throws UnsupportedAssertionNameException si aucun executor n'est enregistre
     */
    AssertionExecutor getFor(String assertionName) throws UnsupportedAssertionNameException;

    /**
     * Retourne l'ensemble des noms d'assertion supportes.
     *
     * @return un ensemble immuable des assertionNames (potentiellement vide)
     */
    Set<String> getSupportedAssertionNames();
}
