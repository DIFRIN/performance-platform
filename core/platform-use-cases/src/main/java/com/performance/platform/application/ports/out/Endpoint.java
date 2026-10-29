package com.performance.platform.application.ports.out;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Description d'une cible a endpoint nomme, independante de tout framework.
 * <p>
 * Type de transport du port {@link EndpointResolver} : il permet aux
 * plugins d'assertion de construire leur propre client sans dependre de
 * {@code platform-infrastructure}.
 *
 * @param baseUrl           URL de base de la cible
 * @param connectionTimeout timeout de connexion
 * @param readTimeout       timeout de lecture
 * @param defaultHeaders    en-tetes par defaut
 * @param paths             mapping chemin logique -> chemin physique
 */
public record Endpoint(
        String baseUrl,
        Duration connectionTimeout,
        Duration readTimeout,
        Map<String, String> defaultHeaders,
        Map<String, String> paths
) {
    public Endpoint {
        Objects.requireNonNull(baseUrl, "baseUrl required");
        connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
        defaultHeaders = defaultHeaders != null ? Map.copyOf(defaultHeaders) : Map.of();
        paths = paths != null ? Map.copyOf(paths) : Map.of();
    }
}
