package com.performance.platform.app.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietes de configuration du chargement des plugins.
 * Prefixe : {@code platform.plugins}
 *
 * <p>Chaque champ correspond a une propriete dans {@code application.yaml} :
 * <pre>
 * platform:
 *   plugins:
 *     dir: /opt/performance-platform/plugins
 *     enabled: true
 * </pre>
 *
 * <p>Si {@code dir} est absent, la valeur par defaut est {@code "plugins"}.
 * Si {@code enabled} est absent, la valeur par defaut est {@code false}.
 *
 * <h3>CF-06 — Chargement au demarrage uniquement</h3>
 * <p>Les JARs du repertoire {@code platform.plugins.dir} sont charges
 * une seule fois au demarrage. Pas de chargement a chaud.
 * Un JAR invalide genere un warning et est ignore — pas un crash.</p>
 *
 * @param dir     le repertoire contenant les JARs plugins
 * @param enabled true pour activer le chargement des plugins au demarrage
 */
@ConfigurationProperties(prefix = "platform.plugins")
public record PluginProperties(String dir, boolean enabled) {

    public PluginProperties {
        if (dir == null || dir.isBlank()) {
            dir = "plugins";
        }
    }
}
