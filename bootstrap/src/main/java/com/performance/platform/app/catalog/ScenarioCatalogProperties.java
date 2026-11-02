package com.performance.platform.app.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietes de configuration du catalogue de scenarios, prefixe
 * {@code platform.scenarios}.
 * <p>
 * Le catalogue est en lecture seule : chaque fichier {@code *.yaml} /
 * {@code *.yml} present dans {@code directory} est parse et expose par l'IHM.
 * Aucun scenario n'est execute au chargement.
 * <p>
 * Configuration via {@code platform.scenarios.directory} ou la variable
 * d'environnement {@code SCENARIOS_DIR} (ADR-006 : env prioritaire).
 * Un repertoire vide ou absent produit un catalogue vide.
 *
 * @param directory repertoire des fichiers de scenarios ; peut etre vide
 */
@ConfigurationProperties(prefix = "platform.scenarios")
public record ScenarioCatalogProperties(String directory) {
}
