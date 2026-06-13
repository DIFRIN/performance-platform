package com.performance.platform.domain.injection;

/**
 * Types de modèle de charge supportés par la plateforme.
 * Correspond aux modèles Gatling : ramp, constant, spike, etc.
 */
public enum LoadModelType {
    RAMP,
    RAMP_UP_DOWN,
    CONSTANT,
    SPIKE,
    STAIR,
    SOAK,
    BURST,
    CUSTOM
}
