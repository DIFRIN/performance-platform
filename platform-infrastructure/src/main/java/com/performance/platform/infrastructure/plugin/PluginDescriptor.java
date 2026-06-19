package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.plugin.TaskExecutor;

import java.util.Objects;

/**
 * Descriptor produit par {@link AnnotationScanner} apres analyse d'une classe candidate.
 * Contient les metadonnees extraites de l'annotation et la phase deduite.
 *
 * @param name          nom de la tache (extrait de {@code name()} de l'annotation)
 * @param version       version du plugin (extrait de {@code version()} de l'annotation)
 * @param description   description du plugin (extrait de {@code description()} de l'annotation)
 * @param phase         phase deduite de l'annotation presente
 * @param executorClass la classe du {@link TaskExecutor}
 */
public record PluginDescriptor(String name, String version, String description,
                               Phase phase, Class<? extends TaskExecutor> executorClass) {

    public PluginDescriptor {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(version, "version must not be null");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(executorClass, "executorClass must not be null");
    }
}
