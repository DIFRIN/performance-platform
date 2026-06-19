package com.performance.platform.infrastructure.plugin;

import java.nio.file.Path;

/**
 * Charge les plugins JAR exterieurs depuis un repertoire au demarrage.
 *
 * <p>Le chargement est one-shot (CF-06) : scan du repertoire, ouverture des JARs,
 * scan des annotations {@code @Preparation/@Injection/@Assertion}, instanciation
 * des {@link com.performance.platform.plugin.TaskExecutor}, et retour d'un
 * {@link PluginLoadResult} recapitulatif.</p>
 *
 * <p>Les erreurs sont non-bloquantes : un JAR corrompu est ignore et reporte
 * dans le {@link PluginLoadResult}, la plateforme continue de demarrer.</p>
 */
public interface PluginLoader {

    /**
     * Charge tous les plugins du repertoire specifie.
     *
     * @param pluginDirectory le repertoire contenant les JARs plugins (non-null)
     * @return le resultat du chargement avec les compteurs, avertissements et erreurs
     * @throws NullPointerException     si pluginDirectory est null
     * @throws IllegalArgumentException si pluginDirectory n'est pas un repertoire
     */
    PluginLoadResult load(Path pluginDirectory);
}
