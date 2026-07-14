package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionSample;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitaire statique de conversion entre {@link AssertionResult} et
 * {@link TaskResult}. Extraite de {@code DagPhaseExecutor} pour etre
 * reutilisable par tous les modules (engine, agents, interface default).
 *
 * <p>Classe finale, constructeur prive — 0 annotation Spring.
 */
public final class AssertionResultMapper {

    private AssertionResultMapper() {}

    /**
     * Convertit un {@link AssertionResult} en {@link TaskResult}, sans historique
     * d'echantillons.
     *
     * @param assertionResult le resultat d'evaluation de l'assertion
     * @param step            la definition de l'etape (pour le {@code taskName})
     * @return le {@link TaskResult} correspondant, avec la cle {@code "assertion"}
     *         dans les outputs contenant un {@link AssertionSummary}
     */
    public static TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step) {
        return toTaskResult(assertionResult, step, List.of());
    }

    /**
     * Convertit un {@link AssertionResult} en {@link TaskResult}, avec un
     * historique explicite d'echantillons.
     *
     * @param assertionResult le resultat d'evaluation de l'assertion
     * @param step            la definition de l'etape (pour le {@code taskName})
     * @param history         l'historique des echantillons collectes
     * @return le {@link TaskResult} correspondant, avec la cle {@code "assertion"}
     *         dans les outputs contenant un {@link AssertionSummary}
     */
    public static TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step, List<AssertionSample> history) {
        TaskStatus taskStatus = mapStatus(assertionResult.status());

        Map<String, Object> collectedData = buildCollectedData(assertionResult.evidence());

        AssertionSummary summary = new AssertionSummary(
                assertionResult.assertionId(),
                assertionResult.status(),
                assertionResult.description(),
                collectedData,
                history,
                assertionResult.evaluationDuration(),
                assertionResult.evaluatedAt()
        );

        Map<String, Object> outputs = Map.of("assertion", summary);

        String errorMessage = (taskStatus == TaskStatus.FAILED) ? assertionResult.description() : null;

        return new TaskResult(
                assertionResult.assertionId(),
                step.taskName(),
                taskStatus,
                assertionResult.evaluationDuration(),
                outputs,
                errorMessage,
                null,
                assertionResult.evaluatedAt()
        );
    }

    /**
     * Extrait un {@link AssertionSummary} depuis les outputs d'un {@link TaskResult}.
     *
     * @param taskResult le resultat de tache potentiellement contenant un summary
     * @return l'{@link AssertionSummary} si present et du bon type, {@code null} sinon
     */
    public static AssertionSummary extractSummary(TaskResult taskResult) {
        if (taskResult == null || taskResult.outputs() == null) {
            return null;
        }
        Object assertionObj = taskResult.outputs().get("assertion");
        if (assertionObj instanceof AssertionSummary summary) {
            return summary;
        }
        return null;
    }

    private static TaskStatus mapStatus(AssertionStatus assertionStatus) {
        return switch (assertionStatus) {
            case PASSED -> TaskStatus.SUCCESS;
            case FAILED, ERROR -> TaskStatus.FAILED;
            case SKIPPED -> TaskStatus.SKIPPED;
        };
    }

    private static Map<String, Object> buildCollectedData(Evidence evidence) {
        if (evidence == null) {
            return Map.of();
        }
        Map<String, Object> data = new HashMap<>();
        if (evidence.actualValue() != null) {
            data.put("actual", evidence.actualValue());
        }
        if (evidence.expectedValue() != null) {
            data.put("expected", evidence.expectedValue());
        }
        data.put("operator", evidence.operator().name());
        if (evidence.unit() != null) {
            data.put("unit", evidence.unit());
        }
        data.putAll(evidence.details());
        return Map.copyOf(data);
    }
}
