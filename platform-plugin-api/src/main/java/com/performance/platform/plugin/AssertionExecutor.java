package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrat pour les plugins d'assertion (interne ET externe).
 * Toute implementation d'assertion doit implementer cette interface et etre annotee
 * avec {@code @Assertion}.
 *
 * <p>Les evaluations d'assertion ne lanceront jamais d'exception —
 * elles retournent un {@link AssertionResult} avec le statut approprie.</p>
 *
 * <p>Depuis v2.x : etend {@link TaskExecutor} pour permettre aux moteurs
 * d'execution de traiter les assertions comme des taches standard via
 * {@link #execute(ExecutionContext, StepDefinition)}. Cette evolution est
 * <b>strictement additive</b> : toutes les implementations existantes d'assertion
 * heritent automatiquement du comportement par defaut sans aucune modification.
 * 0 breaking change.</p>
 *
 * <p>0 annotation framework — interface Java pure.</p>
 *
 * @see com.performance.platform.plugin.Assertion
 * @see TaskExecutor
 */
public interface AssertionExecutor extends TaskExecutor {

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

    /**
     * Execute l'assertion comme une tache standard et retourne un
     * {@link TaskResult}. L'implementation par defaut appelle
     * {@link #evaluate(ExecutionContext, StepDefinition)} et convertit le
     * {@link AssertionResult} en {@link TaskResult} avec un
     * {@link AssertionSummary} place dans les outputs sous la cle
     * {@code "assertion"}.
     *
     * <p>Evolution additive — les implementations existantes qui ne
     * surchargent pas cette methode heritent automatiquement de ce
     * comportement.</p>
     *
     * @param context le contexte d'execution
     * @param step    la definition de l'etape
     * @return le resultat de l'execution sous forme de {@link TaskResult}
     */
    @Override
    default TaskResult execute(ExecutionContext context, StepDefinition step) {
        AssertionResult result = evaluate(context, step);
        return toTaskResult(result);
    }

    /**
     * Delegue a {@link #getSupportedAssertionName()}. Les moteurs
     * d'execution peuvent ainsi decouvrir le nom de tache via l'interface
     * {@link TaskExecutor} sans couplage direct a {@link AssertionExecutor}.
     *
     * @return le nom d'assertion utilise comme nom de tache
     */
    @Override
    default String getSupportedTaskName() {
        return getSupportedAssertionName();
    }

    /**
     * Convertit un {@link AssertionResult} en {@link TaskResult} avec un
     * {@link AssertionSummary} place dans les outputs sous la cle
     * {@code "assertion"}.
     *
     * <p>Mapping des statuts :
     * <ul>
     *   <li>{@code PASSED}  → {@code SUCCESS}</li>
     *   <li>{@code FAILED}  → {@code FAILED}</li>
     *   <li>{@code SKIPPED} → {@code SKIPPED}</li>
     *   <li>{@code ERROR}   → {@code FAILED}</li>
     * </ul>
     *
     * @param result le resultat d'evaluation de l'assertion
     * @return le {@link TaskResult} correspondant
     */
    private TaskResult toTaskResult(AssertionResult result) {
        TaskStatus taskStatus = switch (result.status()) {
            case PASSED  -> TaskStatus.SUCCESS;
            case FAILED  -> TaskStatus.FAILED;
            case SKIPPED -> TaskStatus.SKIPPED;
            case ERROR   -> TaskStatus.FAILED;
        };

        Map<String, Object> collectedData = new HashMap<>();
        if (result.evidence() != null) {
            if (result.evidence().actualValue() != null) {
                collectedData.put("actualValue", result.evidence().actualValue());
            }
            if (result.evidence().expectedValue() != null) {
                collectedData.put("expectedValue", result.evidence().expectedValue());
            }
            collectedData.put("operator", result.evidence().operator().name());
            if (result.evidence().unit() != null) {
                collectedData.put("unit", result.evidence().unit());
            }
            collectedData.putAll(result.evidence().details());
        }

        AssertionSummary summary = new AssertionSummary(
                result.assertionId(),
                result.status(),
                result.description(),
                Map.copyOf(collectedData),
                List.of(),
                result.evaluationDuration(),
                result.evaluatedAt());

        return new TaskResult(
                result.assertionId(),
                getSupportedTaskName(),
                taskStatus,
                result.evaluationDuration(),
                Map.of("assertion", summary),
                taskStatus == TaskStatus.FAILED ? result.description() : null,
                null,
                result.evaluatedAt());
    }
}
