# PDR-035 — Unified Assertion Executor Contract

**Module Maven** : `platform-plugin-api`
**Package** : `com.performance.platform.plugin`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/07-assertion-framework.md`, `.claude/workspace/assertion-distributed-analysis.md` section 2.4
**Depend de** : PDR-003 (deja DONE), PDR-034 (AssertionSummary)
**Issues** : ISSUE-150

---

## Responsabilite

Fait evoluer l'interface `AssertionExecutor` pour qu'elle etende `TaskExecutor`. Cette evolution additive permet aux agents distribues de decouvrir et executer les assertions via le meme pipeline que les taches de preparation et d'injection, sans aucun changement cote agent.

L'interface conserve `evaluate()` pour la compatibilite ascendante et fournit une implementation par defaut de `execute()` qui fait le pont `AssertionResult` -> `TaskResult`.

Ce PDR modifie EXACTEMENT un fichier : `AssertionExecutor.java` dans `platform-plugin-api`.

> CF-08 : `platform-plugin-api` est une interface stable. Cette modification est ADDITIVE (ajout d'une superinterface + default method) -- 0 breaking change. Les plugins existants compilent sans modification.

---

## Interfaces Publiques

### AssertionExecutor (modifie)

```java
package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.Map;

/**
 * Contrat pour les plugins d'assertion (interne ET externe).
 * <p>
 * Depuis PDR-035, {@code AssertionExecutor} etend {@link TaskExecutor}.
 * Cela permet aux agents distribues de resoudre et executer les assertions
 * via leur pipeline {@code TaskExecutionPipeline} existant, sans code
 * specifique aux assertions.
 * <p>
 * <b>Backward compatibility</b>: {@link #evaluate(ExecutionContext, StepDefinition)}
 * reste la methode principale. {@link #execute(ExecutionContext, StepDefinition)}
 * est fourni avec une implementation par defaut qui appelle {@code evaluate()}
 * et convertit le resultat. Les implementations existantes continuent de compiler
 * sans changement.
 * <p>
 * 0 annotation framework -- interface Java pure.
 *
 * @see com.performance.platform.plugin.Assertion
 * @see TaskExecutor
 * @since 1.0 (evaluate uniquement)
 * @since 2.0 (extends TaskExecutor + execute() default)
 */
public interface AssertionExecutor extends TaskExecutor {

    /**
     * Execute l'assertion via le contrat {@link TaskExecutor}.
     * <p>
     * Implementation par defaut : appelle {@link #evaluate} et convertit
     * l'{@link AssertionResult} en {@link TaskResult} avec un
     * {@code AssertionSummary} dans les outputs.
     * <p>
     * Les implementations avancees (interval-based assertions) peuvent
     * surcharger cette methode pour un controle plus fin du cycle de
     * vie (setup -> sampling -> teardown -> TaskResult).
     *
     * @param context le contexte d'execution immuable
     * @param step    la definition de l'etape
     * @return le resultat de l'execution (TaskResult avec AssertionSummary dans outputs)
     */
    @Override
    default TaskResult execute(ExecutionContext context, StepDefinition step) {
        var assertionResult = evaluate(context, step);
        return toTaskResult(assertionResult, step);
    }

    /**
     * Herite de {@link TaskExecutor#getSupportedTaskName()}.
     * <p>
     * Par defaut, delegue a {@link #getSupportedAssertionName()}.
     * Les implementations n'ont PAS besoin de surcharger cette methode
     * si {@code getSupportedAssertionName()} retourne la bonne valeur.
     *
     * @return le nom de tache (identique au nom d'assertion)
     */
    @Override
    default String getSupportedTaskName() {
        return getSupportedAssertionName();
    }

    /**
     * Evalue l'assertion dans le contexte d'execution donne.
     * Retourne toujours un {@link AssertionResult} -- jamais d'exception.
     * <p>
     * INCHANGE depuis la version 1.0.
     *
     * @param context le contexte d'execution immuable (cote orchestrateur)
     * @param step    la definition de l'etape contenant les parametres d'assertion
     * @return le resultat de l'evaluation (PASSED, FAILED, SKIPPED, ou ERROR)
     */
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);

    /**
     * Nom de l'assertion supportee. Doit correspondre au {@code name()} de
     * l'annotation {@code @Assertion}.
     * <p>
     * INCHANGE depuis la version 1.0.
     *
     * @return le nom d'assertion (jamais null)
     */
    String getSupportedAssertionName();

    // === Bridge AssertionResult -> TaskResult (private helper dans default method) ===

    /**
     * Convertit un {@link AssertionResult} interne en {@link TaskResult}
     * avec un {@code AssertionSummary} dans {@code outputs["assertion"]}.
     * <p>
     * Cette methode est privee dans l'interface (Java 25).
     * Utilisee par l'implementation par defaut de {@link #execute}.
     */
    private TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step) {
        Map<String, Object> collectedData = (assertionResult.evidence() != null)
                ? assertionResult.evidence().details()
                : Map.of();

        var summary = new com.performance.platform.domain.assertion.AssertionSummary(
                assertionResult.assertionId(),
                assertionResult.status(),
                assertionResult.description(),
                collectedData,
                java.util.List.of(),   // empty history for point-in-time assertions
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
}
```

### TaskExecutor (inchange)

```java
// Aucune modification au contrat TaskExecutor.
// AssertionExecutor devient un sous-type de TaskExecutor via extends.
```

---

## Regles de Comportement

- `execute()` ne leve jamais d'exception metier -- retourne `TaskResult.failed()` (herite de `TaskExecutor`)
- `evaluate()` ne leve jamais d'exception metier -- retourne `AssertionResult` avec `ERROR` (inchange)
- `getSupportedTaskName()` retourne LA MEME valeur que `getSupportedAssertionName()`. Pas de divergence.
- Les 6 executors existants n'ont PAS besoin de changer leur `evaluate()`. Ils heritent `execute()` gratuitement.
- Les 6 executors existants n'ont PAS besoin d'ajouter `getSupportedTaskName()` -- le default delegue a `getSupportedAssertionName()`.
- L'annotation `@Assertion(name="...")` continue de fonctionner pour le `PluginLoader`. Aucun changement.
- Si un executor veut un comportement different (ex: interval-based avec `linkedTo`), il surcharge `execute()`.
- **Parametres YAML `stopBehavior` et `gracePeriodDuration`** : Ces parametres sont lus depuis `StepDefinition.parameters()` au runtime par l'engine (pas par l'interface `AssertionExecutor`). L'engine les transmet dans le `ExecutionLifecycleSignal`. L'executor d'assertion les recoit via le signal, pas via `step.parameters()`. L'interface `AssertionExecutor` n'a pas besoin de connaitre ces parametres dans sa signature.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-001 (platform-domain records)  -> TaskResult, AssertionResult, ExecutionContext, StepDefinition, AssertionSummary (deja STABLE)
  PDR-003 (plugin-api annotations)   -> @Assertion (deja STABLE)

Ce PDR est utilise par :
  PDR-036 (assertion registration)   -> AssertionExecutor beans sont maintenant des TaskExecutor beans
  PDR-037 (engine unified dispatch)   -> DagPhaseExecutor utilise findTaskExecutor() pour les assertions
  Tous les plugins d'assertion        -> heritent automatiquement execute()
```

---

## Criteres de Done (PDR complet)

- [ ] `AssertionExecutor extends TaskExecutor` compile dans `platform-plugin-api`
- [ ] `execute()` default method compile et reference `AssertionSummary`
- [ ] `getSupportedTaskName()` default delegue a `getSupportedAssertionName()`
- [ ] Les 6 executors d'assertion existants compilent sans modification
- [ ] `mvn test -pl platform-plugin-api -q` -> 0 erreur
- [ ] L'interface est dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] ArchUnit : 0 annotation framework dans `platform-plugin-api`
