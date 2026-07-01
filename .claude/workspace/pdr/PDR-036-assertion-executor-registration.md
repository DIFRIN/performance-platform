# PDR-036 — Assertion Executor Registration & Discovery

**Module Maven** : `platform-assertion`, `platform-infrastructure`
**Package** : `com.performance.platform.assertion`, `com.performance.platform.infrastructure.executor`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/07-assertion-framework.md`, `.claude/workspace/assertion-distributed-analysis.md` section 3
**Depend de** : PDR-035 (AssertionExecutor extends TaskExecutor), PDR-010 (TaskExecutorRegistry)
**Issues** : ISSUE-151, ISSUE-152, ISSUE-153

---

## Responsabilite

Apres que `AssertionExecutor extends TaskExecutor` (PDR-035), les implementations d'assertion sont automatiquement des beans `TaskExecutor`. Ce PDR gere la transition des registres et la configuration Spring pour que :

1. Les 6 `AssertionExecutor` beans soient collectes par `DefaultTaskExecutorRegistry` (automatique via Spring)
2. L'ancien `AssertionExecutorRegistry` soit deprecie mais conserve comme wrapper
3. Un utilitaire partage `AssertionResultMapper` extrait la logique de conversion `AssertionResult -> TaskResult` hors de `DagPhaseExecutor`
4. Les configurations Spring necessaires refletent le nouveau modele

---

## Interfaces Publiques

### AssertionResultMapper (nouveau, dans platform-assertion)

```java
package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utilitaire de conversion AssertionResult -> TaskResult.
 * <p>
 * Extrait de {@code DagPhaseExecutor.assertionResultToTaskResult()} pour
 * etre reutilisable par l'engine, les agents, et l'implementation par
 * defaut de {@code AssertionExecutor.execute()}.
 * <p>
 * Thread-safe (stateless). 0 annotation framework.
 */
public final class AssertionResultMapper {

    private AssertionResultMapper() {
        // Utilitaire -- non instanciable
    }

    /**
     * Convertit un {@link AssertionResult} en {@link TaskResult} avec
     * un {@link AssertionSummary} dans {@code outputs["assertion"]}.
     * <p>
     * Point-in-time assertions produisent une history vide.
     *
     * @param assertionResult le resultat d'evaluation interne
     * @param step            la definition de l'etape (pour taskName et taskId)
     * @return un TaskResult pret pour le stockage et le transport
     */
    public static TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step) {
        return toTaskResult(assertionResult, step, List.of());
    }

    /**
     * Convertit avec history explicite (pour interval-based assertions, Phase B).
     *
     * @param assertionResult le resultat interne
     * @param step            la definition de l'etape
     * @param history         les echantillons collectes (vide = point-in-time)
     * @return un TaskResult pret pour le stockage et le transport
     */
    public static TaskResult toTaskResult(
            AssertionResult assertionResult,
            StepDefinition step,
            List<com.performance.platform.domain.assertion.AssertionSample> history
    ) {
        Objects.requireNonNull(assertionResult, "assertionResult required");
        Objects.requireNonNull(step, "step required");
        Objects.requireNonNull(history, "history required");

        Map<String, Object> collectedData = (assertionResult.evidence() != null)
                ? assertionResult.evidence().details()
                : Map.of();

        var summary = new AssertionSummary(
                assertionResult.assertionId(),
                assertionResult.status(),
                assertionResult.description(),
                collectedData,
                history,
                assertionResult.evaluationDuration(),
                assertionResult.evaluatedAt()
        );

        TaskStatus taskStatus = switch (assertionResult.status()) {
            case PASSED  -> TaskStatus.SUCCESS;
            case FAILED  -> TaskStatus.FAILED;
            case SKIPPED -> TaskStatus.SKIPPED;
            case ERROR   -> TaskStatus.FAILED;
        };

        return new TaskResult(
                assertionResult.assertionId(),
                step.taskName(),
                taskStatus,
                assertionResult.evaluationDuration(),
                Map.of("assertion", summary),
                taskStatus == TaskStatus.FAILED ? assertionResult.description() : null,
                null,
                assertionResult.evaluatedAt()
        );
    }

    /**
     * Extrait un {@link AssertionSummary} depuis les outputs d'un {@link TaskResult},
     * si present.
     *
     * @param taskResult le resultat de tache (potentiellement issu d'une assertion)
     * @return l'AssertionSummary, ou {@code null} si absent
     */
    public static AssertionSummary extractSummary(TaskResult taskResult) {
        if (taskResult == null || taskResult.outputs() == null) return null;
        Object assertion = taskResult.outputs().get("assertion");
        if (assertion instanceof AssertionSummary summary) {
            return summary;
        }
        return null;
    }
}
```

### AssertionExecutorRegistry (deprecie mais conserve)

```java
// L'interface AssertionExecutorRegistry est conservee TELLE QUELLE.
// Le commentaire Javadoc suivant est ajoute :
/**
 * @deprecated Depuis PDR-035, {@link AssertionExecutor} etend {@link TaskExecutor}.
 *             Les executors d'assertion sont desormais accessibles via
 *             {@link TaskExecutorRegistry}. Cette interface est conservee
 *             pour la compatibilite ascendante et sera retiree dans une
 *             version future.
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface AssertionExecutorRegistry {
    // ... inchangé
}
```

### DefaultAssertionExecutorRegistry (deprecie mais conserve)

```java
@Component
@Deprecated(since = "2.0", forRemoval = true)
public class DefaultAssertionExecutorRegistry implements AssertionExecutorRegistry {
    // ... inchangé -- toujours fonctionnel
}
```

---

## Regles de Comportement

- Les 6 `AssertionExecutor` beans sont desormais automatiquement collectes par `DefaultTaskExecutorRegistry` car ils implementent `TaskExecutor` via `AssertionExecutor extends TaskExecutor`. Spring injecte tous les beans `TaskExecutor` (y compris les `AssertionExecutor`) dans le constructeur de `DefaultTaskExecutorRegistry`.
- Aucun changement dans les 6 implementations d'assertion. Elles compilent et fonctionnent comme avant.
- `AssertionResultMapper.toTaskResult()` est la reference canonique pour la conversion. Le code duplique dans `DagPhaseExecutor.assertionResultToTaskResult()` est retire (remplace par un appel a `AssertionResultMapper`).
- L'ancien `DefaultAssertionExecutorRegistry` reste fonctionnel pour le code legacy qui l'utilise. Le `TaskExecutorLookup` peut choisir d'utiliser l'un ou l'autre registre.
- La cle `"assertion"` dans `TaskResult.outputs` est reservee pour les `AssertionSummary`. Les autres executors (preparation/injection) ne doivent pas utiliser cette cle.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-034 (AssertionSummary)          -> AssertionSummary, AssertionSample
  PDR-035 (plugin-api)                -> AssertionExecutor extends TaskExecutor
  PDR-010 (TaskExecutorRegistry)      -> DefaultTaskExecutorRegistry recoit AssertionExecutor beans
  PDR-014 (Assertion executors)       -> les 6 implementations existantes (deja DONE)

Ce PDR est utilise par :
  PDR-037 (engine unified dispatch)   -> DagPhaseExecutor utilise AssertionResultMapper
```

---

## Criteres de Done (PDR complet)

- [ ] `AssertionResultMapper` compile dans `platform-assertion` (0 erreur)
- [ ] `AssertionResultMapper` couvert par tests unitaires (Tous les statuts: PASSED, FAILED, SKIPPED, ERROR + history vide/non-vide + extractSummary avec null/missing/wrong-type)
- [ ] `DefaultTaskExecutorRegistry` recoit automatiquement les 6 `AssertionExecutor` beans (verifie via test Spring)
- [ ] `AssertionExecutorRegistry` et `DefaultAssertionExecutorRegistry` sont annotes `@Deprecated(since="2.0", forRemoval=true)`
- [ ] `DagPhaseExecutor.assertionResultToTaskResult()` est remplace par l'appel a `AssertionResultMapper.toTaskResult()`
- [ ] `mvn test -pl platform-assertion -q` -> 0 erreur
- [ ] `mvn test -pl platform-infrastructure -q` -> 0 erreur
