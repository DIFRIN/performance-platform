package com.performance.platform.app.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Etape d'un scenario exposee par le catalogue.
 *
 * @param id               identifiant de l'etape dans le scenario
 * @param taskName         nom de tache (task executor)
 * @param phase            PREPARATION / INJECTION / ASSERTION, {@code null} pour une service step
 * @param during           INJECTION / SCENARIO pour une service step, {@code null} sinon
 * @param ref              etape referencee (ADR-053), {@code null} si aucun
 * @param fanOut           one / all (ADR-055)
 * @param dependsOn        identifiants des etapes prealables
 * @param requiredContexts cles de contexte requises
 * @param timeoutMillis    timeout en millisecondes, {@code null} si absent
 * @param parameters       parametres libres de l'etape
 */
public record CatalogStepResponse(
        String id,
        String taskName,
        String phase,
        String during,
        String ref,
        String fanOut,
        List<String> dependsOn,
        List<String> requiredContexts,
        Long timeoutMillis,
        Map<String, Object> parameters) {
}
