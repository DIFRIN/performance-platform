package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionSummaries;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

/**
 * Contrat unique des assertions (interne ET plugin externe).
 * <p>
 * Une implementation ne juge pas : elle produit des
 * {@link com.performance.platform.domain.assertion.Check} et les regroupe dans
 * un {@link AssertionSummary}. Le statut global, le libelle et la conversion en
 * {@link TaskResult} sont fournis par le core, ce qui garantit un verdict
 * identique partout (ADR-049).
 *
 * <p>0 annotation framework — interface Java pure.</p>
 *
 * @see Assertion
 * @see TaskExecutor
 */
public interface AssertionExecutor extends TaskExecutor {

    /**
     * Evalue l'assertion et retourne son resume complet (checks + SLO + verdict).
     * Ne lance jamais d'exception metier : un echec d'evaluation produit un
     * check {@code ERROR}.
     *
     * @param context le contexte d'execution immuable
     * @param step    la definition de l'etape d'assertion
     * @return le resume d'assertion, jamais null
     */
    AssertionSummary evaluate(ExecutionContext context, StepDefinition step);

    /**
     * Nom de l'assertion supportee ; doit correspondre au {@code name()} de
     * l'annotation {@code @Assertion}.
     */
    String getSupportedAssertionName();

    @Override
    default String getSupportedTaskName() {
        return getSupportedAssertionName();
    }

    /**
     * Execute l'assertion comme une tache standard. La conversion
     * resume -&gt; {@link TaskResult} est centralisee dans
     * {@link AssertionSummaries} : une seule table de correspondance pour toute
     * la plateforme.
     */
    @Override
    default TaskResult execute(ExecutionContext context, StepDefinition step) {
        return AssertionSummaries.toTaskResult(evaluate(context, step), getSupportedAssertionName());
    }
}
