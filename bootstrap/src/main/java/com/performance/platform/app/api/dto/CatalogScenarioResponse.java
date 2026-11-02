package com.performance.platform.app.api.dto;

import java.util.List;

/**
 * Entree du catalogue de scenarios, avec ses etapes pour visualisation.
 *
 * @param key          nom du fichier, identifiant stable dans le catalogue
 * @param scenarioId   identifiant du scenario declare dans le YAML
 * @param name         nom du scenario
 * @param version      version du scenario
 * @param tags         tags du scenario
 * @param executionMode mode d'execution declare (LOCAL / DISTRIBUTED)
 * @param stepCount    nombre d'etapes
 * @param valid        true si le fichier a ete parse avec succes
 * @param error        message d'erreur de parsing, {@code null} si valide
 * @param yaml         contenu YAML brut, {@code null} si illisible
 * @param steps        etapes du scenario (vide si invalide)
 */
public record CatalogScenarioResponse(
        String key,
        String scenarioId,
        String name,
        String version,
        List<String> tags,
        String executionMode,
        int stepCount,
        boolean valid,
        String error,
        String yaml,
        List<CatalogStepResponse> steps) {
}
