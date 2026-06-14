package com.performance.platform.scenario.validation;

/**
 * Port entrant : valider un {@link com.performance.platform.domain.scenario.ScenarioDefinition}.
 * Produit un {@link ValidationResult} contenant les erreurs bloquantes et les warnings.
 */
public interface ScenarioValidator {

    /**
     * Valide la definition complete d'un scenario.
     *
     * @param scenario la definition de scenario a valider
     * @return le resultat de validation (erreurs + warnings)
     */
    ValidationResult validate(com.performance.platform.domain.scenario.ScenarioDefinition scenario);
}
