package com.performance.platform.domain.assertion;

import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.Map;

/**
 * Conversions uniques entre {@link AssertionSummary} et {@link TaskResult}.
 * <p>
 * Ce helper vit dans le domaine afin d'etre partage par
 * {@code platform-plugin-api} et {@code platform-assertion} sans dependance
 * circulaire.
 * <p>
 * Regles de mapping, uniques pour toute la plateforme :
 * <ul>
 *   <li>PASSED -&gt; SUCCESS</li>
 *   <li>FAILED / ERROR -&gt; FAILED</li>
 *   <li>SKIPPED -&gt; SKIPPED</li>
 *   <li>INCONCLUSIVE -&gt; SUCCESS (la mesure manque, l'execution reste valide ;
 *       le rapport en fait une limitation, pas un echec technique)</li>
 * </ul>
 */
public final class AssertionSummaries {

    private AssertionSummaries() {
    }

    /** Construit un {@link TaskResult} depuis un resume d'assertion. */
    public static TaskResult toTaskResult(AssertionSummary summary, String taskName) {
        TaskStatus taskStatus = mapStatus(summary.verdict());
        String errorMessage = taskStatus == TaskStatus.FAILED ? summary.description() : null;
        return new TaskResult(
                summary.assertionId(),
                taskName,
                taskStatus,
                summary.evaluationDuration(),
                Map.of(AssertionSummary.OUTPUT_KEY, summary),
                errorMessage,
                null,
                summary.evaluatedAt());
    }

    /** Mapping unique statut d'assertion vers statut de tache. */
    public static TaskStatus mapStatus(AssertionStatus status) {
        return switch (status) {
            case PASSED, INCONCLUSIVE -> TaskStatus.SUCCESS;
            case FAILED, ERROR -> TaskStatus.FAILED;
            case SKIPPED -> TaskStatus.SKIPPED;
        };
    }

    /** Extrait un {@link AssertionSummary} depuis les outputs d'un {@link TaskResult}. */
    public static AssertionSummary extractSummary(TaskResult taskResult) {
        if (taskResult == null || taskResult.outputs() == null) {
            return null;
        }
        Object value = taskResult.outputs().get(AssertionSummary.OUTPUT_KEY);
        return value instanceof AssertionSummary summary ? summary : null;
    }

    /** Extrait le {@link SloStatement} d'une etape (bloc {@code description}). */
    public static SloStatement sloStatement(StepDefinition step) {
        if (step == null || step.parameters() == null) {
            return SloStatement.empty();
        }
        return sloStatement(step.parameters().get("description"));
    }

    /** Extrait le {@link SloStatement} d'un bloc YAML deja parse. */
    public static SloStatement sloStatement(Object description) {
        if (!(description instanceof Map<?, ?> map)) {
            return SloStatement.empty();
        }
        return new SloStatement(
                text(map.get("title")),
                text(map.get("sli")),
                text(map.get("slo")),
                text(map.get("rationale")));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }
}
