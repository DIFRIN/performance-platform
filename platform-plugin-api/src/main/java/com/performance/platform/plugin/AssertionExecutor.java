package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;

/**
 * Contrat pour les plugins d'assertion (interne ET externe).
 * Toute implementation d'assertion doit implementer cette interface et etre annotee
 * avec {@code @Assertion}.
 *
 * <p>Les evaluations d'assertion ne lanceront jamais d'exception —
 * elles retournent un {@link AssertionResult} avec le statut approprie.</p>
 *
 * <p>0 annotation framework — interface Java pure.</p>
 *
 * @see com.performance.platform.plugin.Assertion
 */
public interface AssertionExecutor {

    /**
     * Evalue l'assertion dans le contexte d'execution donne.
     * Retourne toujours un {@link AssertionResult} — jamais d'exception.
     *
     * @param context le contexte d'execution immuable (cote orchestrateur)
     * @param step    la definition de l'etape contenant les parametres d'assertion
     * @return le resultat de l'evaluation (PASSED, FAILED, SKIPPED, ou ERROR)
     */
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);

    /**
     * Nom de l'assertion supportee. Doit correspondre au {@code name()} de
     * l'annotation {@code @Assertion}.
     *
     * @return le nom d'assertion (jamais null)
     */
    String getSupportedAssertionName();
}
