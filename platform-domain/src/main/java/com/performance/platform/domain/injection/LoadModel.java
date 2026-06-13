package com.performance.platform.domain.injection;

import java.util.Map;
import java.util.Objects;

/**
 * Modèle de charge décrivant le profil d'injection (utilisateurs virtuels, durée, rampe, etc.).
 * Record immuable — copies défensives sur les paramètres.
 * 0 annotation framework.
 */
public record LoadModel(LoadModelType type, Map<String, Object> parameters) {
    public LoadModel {
        Objects.requireNonNull(type, "type required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
