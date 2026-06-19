package com.performance.platform.infrastructure.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultat du chargement des plugins par le {@link PluginLoader}.
 *
 * @param jarsLoaded          nombre de fichiers JAR traites
 * @param executorsRegistered nombre de {@link com.performance.platform.plugin.TaskExecutor} instancies avec succes
 * @param warnings            avertissements non-bloquants (collisions, annotations invalides...)
 * @param errors              erreurs non-bloquantes (JAR corrompu, classe non-instanciable...)
 */
public record PluginLoadResult(int jarsLoaded, int executorsRegistered,
                               List<PluginWarning> warnings, List<PluginError> errors) {

    public PluginLoadResult {
        if (jarsLoaded < 0) {
            throw new IllegalArgumentException("jarsLoaded must be >= 0, was: " + jarsLoaded);
        }
        if (executorsRegistered < 0) {
            throw new IllegalArgumentException("executorsRegistered must be >= 0, was: " + executorsRegistered);
        }
        // Defensive copy — immutability
        warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * @return true si au moins une erreur est survenue pendant le chargement
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * @return true si au moins un avertissement a ete emis
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
