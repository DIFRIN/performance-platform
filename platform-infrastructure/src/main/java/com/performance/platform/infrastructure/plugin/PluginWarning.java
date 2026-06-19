package com.performance.platform.infrastructure.plugin;

/**
 * Avertissement non-bloquant lors du chargement d'un plugin.
 * Exemple : collision de nom avec un executor interne, annotation invalide.
 *
 * @param pluginJar le nom du fichier JAR concerne
 * @param message   description de l'avertissement
 */
public record PluginWarning(String pluginJar, String message) {

    public PluginWarning {
        if (pluginJar == null || pluginJar.isBlank()) {
            throw new IllegalArgumentException("pluginJar must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
