package com.performance.platform.infrastructure.plugin;

import java.util.Objects;

/**
 * Erreur non-bloquante lors du chargement d'un plugin.
 * Le JAR est ignore mais le demarrage continue normalement.
 *
 * @param pluginJar le nom du fichier JAR concerne
 * @param message   description de l'erreur
 * @param cause     l'exception a l'origine de l'erreur (peut etre null)
 */
public record PluginError(String pluginJar, String message, Throwable cause) {

    public PluginError {
        if (pluginJar == null || pluginJar.isBlank()) {
            throw new IllegalArgumentException("pluginJar must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Factory pour une erreur sans cause.
     */
    public static PluginError of(String pluginJar, String message) {
        return new PluginError(pluginJar, message, null);
    }

    /**
     * Factory pour une erreur avec cause.
     */
    public static PluginError of(String pluginJar, String message, Throwable cause) {
        return new PluginError(pluginJar, message, Objects.requireNonNull(cause, "cause must not be null"));
    }
}
