package com.performance.platform.app.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietes de configuration de l'IHM web, prefixe {@code web.ui}.
 * <p>
 * Controle l'activation de l'interface utilisateur web.
 * Par defaut desactivee ({@code enabled=false}) pour tous les profils.
 * Le profil AGENT force {@code false} explicitement
 * (garantie forte via {@code WebApplicationType.NONE} dans {@code main()} — ADR-019).
 * <p>
 * Activation via {@code web.ui.enabled=true} dans la configuration
 * ou via la variable d'environnement {@code WEB_UI_ENABLED=true}
 * (relaxed binding Spring Boot natif).
 *
 * @param enabled si l'IHM web est activee ; defaut {@code false}
 */
@ConfigurationProperties(prefix = "web.ui")
public record WebUiProperties(boolean enabled) {
}
