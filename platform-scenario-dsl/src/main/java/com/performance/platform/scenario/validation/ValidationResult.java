package com.performance.platform.scenario.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resultat d'une validation de scenario.
 * Porte la liste des erreurs bloquantes (rendant le scenario invalide)
 * et des warnings (non bloquants).
 * Record immuable.
 */
public record ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Cree un resultat valide (aucune erreur).
     */
    public static ValidationResult valid(List<ValidationWarning> warnings) {
        return new ValidationResult(true, List.of(), warnings != null ? warnings : List.of());
    }

    /**
     * Cree un resultat invalide.
     */
    public static ValidationResult invalid(List<ValidationError> errors, List<ValidationWarning> warnings) {
        return new ValidationResult(false, errors != null ? errors : List.of(),
            warnings != null ? warnings : List.of());
    }

    /**
     * Retourne la liste des messages d'erreur pour un affichage rapide.
     */
    public List<String> errorMessages() {
        return errors.stream()
            .map(e -> e.field() + ": " + e.message())
            .toList();
    }
}
