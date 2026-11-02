package com.performance.platform.app.catalog;

import com.performance.platform.domain.scenario.ScenarioDefinition;

/**
 * Entree du catalogue de scenarios.
 * <p>
 * {@code definition} est {@code null} quand le fichier n'a pas pu etre parse ;
 * {@code error} porte alors le message d'erreur. {@code yaml} conserve le
 * contenu brut pour affichage dans l'IHM.
 *
 * @param key        nom du fichier, identifiant stable dans le catalogue
 * @param yaml       contenu YAML brut
 * @param definition definition parsee, {@code null} si invalide
 * @param error      message d'erreur de parsing, {@code null} si valide
 */
public record CatalogScenario(
        String key,
        String yaml,
        ScenarioDefinition definition,
        String error) {

    /** True quand le scenario a ete parse avec succes. */
    public boolean valid() {
        return definition != null;
    }
}
