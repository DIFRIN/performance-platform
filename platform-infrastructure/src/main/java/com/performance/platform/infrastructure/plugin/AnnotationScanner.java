package com.performance.platform.infrastructure.plugin;

import java.util.Optional;

/**
 * Scanne une classe candidate pour detecter la presence d'exactement une annotation
 * de plugin ({@code @Preparation}, {@code @Injection}, ou {@code @Assertion})
 * et en extrait les metadonnees.
 *
 * <p>Contrat :
 * <ul>
 *   <li>Exactement une annotation → {@link Optional} contenant un {@link PluginDescriptor}</li>
 *   <li>Zero annotation → {@link Optional#empty()} (classe non-plugin, skip silencieux)</li>
 *   <li>Plusieurs annotations → {@link Optional#empty()} avec log WARN (ambiguite)</li>
 * </ul>
 *
 * <p>Reflexion pure — aucune dependance Spring.
 */
public interface AnnotationScanner {

    /**
     * Analyse une classe candidate.
     *
     * @param candidate la classe a scanner (non-null)
     * @return un {@link PluginDescriptor} si exactement une annotation de plugin est presente,
     *         {@link Optional#empty()} sinon
     */
    Optional<PluginDescriptor> scan(Class<?> candidate);
}
