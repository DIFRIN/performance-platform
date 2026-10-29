package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.LoadModel;
import io.gatling.javaapi.core.OpenInjectionStep;

import java.util.List;

/**
 * Traduit un {@link LoadModel} (modele de charge abstrait) en une liste
 * d'{@link OpenInjectionStep} Gatling Java DSL.
 * <p>
 * Chaque {@link com.performance.platform.domain.injection.LoadModelType}
 * possede sa propre strategie de traduction vers les etapes d'injection
 * Gatling correspondantes.
 * <p>
 * <strong>Thread-safe</strong> : les implementations doivent etre sans
 * etat mutable.
 */
@FunctionalInterface
public interface LoadModelTranslator {

    /**
     * Traduit le modele de charge donne en etapes d'injection Gatling.
     *
     * @param model le modele de charge a traduire
     * @return la liste des etapes d'injection Gatling
     * @throws IllegalArgumentException si les parametres sont invalides
     *         ou incomplets pour le type de modele
     */
    List<OpenInjectionStep> translate(LoadModel model);
}
