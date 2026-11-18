package com.performance.platform.plugin.resource;

import java.util.Map;
import java.util.Objects;

/**
 * Description serialisable d'un {@link MessageChannel} : permet a un autre processus (worker
 * d'injection, ADR-071) de rouvrir son propre canal. Aucun secret : {@code settings} est ecrit
 * dans le run directory.
 *
 * @param type     technologie, egale au {@link ChannelOpener#type()} qui sait la rouvrir
 * @param settings parametres de connexion (destination physique comprise), valeurs textuelles
 */
public record ChannelDescriptor(String type, Map<String, String> settings) {

    public ChannelDescriptor {
        Objects.requireNonNull(type, "type must not be null");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }
}
