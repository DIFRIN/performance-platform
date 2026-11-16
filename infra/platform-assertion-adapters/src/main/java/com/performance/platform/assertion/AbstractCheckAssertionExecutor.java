package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.AssertionExecutor;

/**
 * Base commune des assertions multi-checks.
 * <p>
 * Un seul comportement pour toute la plateforme : l'adapter produit des
 * {@link com.performance.platform.domain.assertion.Check}, la fabrique calcule
 * le verdict et le {@link TaskResult} contient l'{@code AssertionSummary}
 * complet (checks + SLO). Aucun adapter ne choisit le statut mappe.
 */
public abstract class AbstractCheckAssertionExecutor implements AssertionExecutor {

    /** Evalue l'assertion et retourne son resume complet. */
    protected abstract AssertionSummary evaluateChecks(ExecutionContext context, StepDefinition step);

    @Override
    public final AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
        return evaluateChecks(context, step);
    }

    @Override
    public final TaskResult execute(ExecutionContext context, StepDefinition step) {
        return AssertionExecutor.super.execute(context, step);
    }
}
