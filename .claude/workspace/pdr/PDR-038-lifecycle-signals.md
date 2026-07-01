# PDR-038 — Lifecycle Signals (ExecutionLifecycleSignal)

**Module Maven** : `platform-domain`
**Package** : `com.performance.platform.domain.event`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/02-execution-engine.md` section 8, `.claude/knowledge/specs/05-transport-layer.md` section 3, `.claude/workspace/assertion-distributed-analysis.md` section 4
**Depend de** : PDR-002 (AgentSignal domain events, deja DONE), PDR-001 (SignalId, ExecutionId, TaskId, deja DONE)
**Issues** : ISSUE-157

---

## Responsabilite

Definit le signal de cycle de vie `ExecutionLifecycleSignal` qui est envoye par l'engine a tous les agents avant et apres l'execution de chaque tache. Ce signal est un signal generaliste (toutes les tasks, pas seulement les assertions). Il remplace la conception precedente de `PhaseSignal` specifique aux assertions par un mecanisme uniforme applicables a tous les types de taches.

Ce PDR modifie UNIQUEMENT `platform-domain` :
1. Ajoute `LifecycleAction` enum (START, STOP)
2. Ajoute `ExecutionLifecycleSignal` record implementant `AgentSignal`
3. Met a jour la clause `permits` de `AgentSignal` pour inclure `ExecutionLifecycleSignal`

Aucune modification de `platform-application`, `platform-transport`, ou autre module.

---

## Interfaces Publiques

### LifecycleAction (nouvel enum)

```java
package com.performance.platform.domain.event;

/**
 * Action associee a un signal de cycle de vie d'execution.
 * <p>
 * START : L'engine signale le debut prochain d'une execution de task.
 *         L'agent doit se preparer (allouer ressources, ouvrir connexions).
 *         Pour les assertions avec linkedTo : l'agent demarre une boucle de sampling.
 * <p>
 * STOP  : L'engine signale la fin d'une execution de task.
 *         L'agent doit finaliser (produire resultat final, nettoyer ressources).
 *         Pour les assertions avec linkedTo : l'agent arrete le sampling,
 *         applique le stopBehavior, et publie TaskCompleted.
 * <p>
 * 0 annotation framework.
 */
public enum LifecycleAction {
    START,
    STOP
}
```

---

### ExecutionLifecycleSignal (nouveau record)

```java
package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Signal de cycle de vie envoye par l'engine vers les agents
 * avant (START) et apres (STOP) l'execution d'une task.
 * <p>
 * Ce signal est un signal generaliste : il est envoye pour TOUTES les tasks,
 * pas seulement les assertions. Les assertions avec {@code linkedTo} utilisent
 * START/STOP pour delimiter leur fenetre de monitoring concurrente avec l'injection.
 * <p>
 * Transporte via {@link ExecutionTransport#broadcastSignal(AgentSignal)}.
 * Tous les agents recoivent le signal. L'agent specialise pour le {@code taskId}
 * du signal le traite ; les autres l'ignorent silencieusement.
 * <p>
 * Record immuable -- 0 annotation framework. Copies defensives sur parameters.
 */
public record ExecutionLifecycleSignal(
    SignalId id,
    ExecutionId executionId,
    TaskId taskId,
    LifecycleAction action,           // START or STOP
    Map<String, Object> parameters,   // parametres transmis par l'engine
    Instant issuedAt
) implements AgentSignal {

    public ExecutionLifecycleSignal {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(action, "action required");
        Objects.requireNonNull(issuedAt, "issuedAt required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /**
     * Cles connues dans le map parameters :
     * <p>
     * Pour les assertions avec linkedTo (START) :
     * <ul>
     *   <li>{@code intervalSeconds} (Long) -- frequence de sampling en secondes</li>
     *   <li>{@code stopBehavior} (String) -- "immediate", "completeCurrentCycle", ou "gracePeriod"</li>
     *   <li>{@code gracePeriodDuration} (String) -- duree ISO-8601, ex: "PT10S"</li>
     * </ul>
     * <p>
     * Pour les assertions avec linkedTo (STOP) :
     * <ul>
     *   <li>{@code stopBehavior} (String) -- comportement d'arret a appliquer</li>
     *   <li>{@code gracePeriodDuration} (String) -- duree de grace period si applicable</li>
     * </ul>
     * <p>
     * Pour les tasks standards (START/STOP) :
     * <ul>
     *   <li>{@code taskName} (String) -- nom de la task concernee</li>
     *   <li>{@code phase} (String) -- phase de la task (PREPARATION, INJECTION, ASSERTION)</li>
     * </ul>
     */
    public static final String PARAM_INTERVAL_SECONDS = "intervalSeconds";
    public static final String PARAM_STOP_BEHAVIOR = "stopBehavior";
    public static final String PARAM_GRACE_PERIOD_DURATION = "gracePeriodDuration";
    public static final String PARAM_TASK_NAME = "taskName";
    public static final String PARAM_PHASE = "phase";

    /**
     * Valeurs valides pour stopBehavior.
     */
    public static final String STOP_IMMEDIATE = "immediate";
    public static final String STOP_COMPLETE_CURRENT_CYCLE = "completeCurrentCycle";
    public static final String STOP_GRACE_PERIOD = "gracePeriod";

    /**
     * Factory pour un signal START avec les parametres de step d'assertion.
     */
    public static ExecutionLifecycleSignal start(
            SignalId signalId,
            ExecutionId executionId,
            TaskId taskId,
            Map<String, Object> parameters
    ) {
        return new ExecutionLifecycleSignal(
                signalId, executionId, taskId,
                LifecycleAction.START, parameters, Instant.now()
        );
    }

    /**
     * Factory pour un signal STOP avec les parametres de stop behavior.
     */
    public static ExecutionLifecycleSignal stop(
            SignalId signalId,
            ExecutionId executionId,
            TaskId taskId,
            Map<String, Object> parameters
    ) {
        return new ExecutionLifecycleSignal(
                signalId, executionId, taskId,
                LifecycleAction.STOP, parameters, Instant.now()
        );
    }

    /**
     * Extrait le stop behavior depuis parameters, avec valeur par defaut.
     *
     * @return "immediate" si non specifie ou valeur non reconnue
     */
    public String stopBehavior() {
        Object val = parameters.get(PARAM_STOP_BEHAVIOR);
        if (val instanceof String s) {
            return switch (s) {
                case STOP_IMMEDIATE, STOP_COMPLETE_CURRENT_CYCLE, STOP_GRACE_PERIOD -> s;
                default -> STOP_IMMEDIATE;
            };
        }
        return STOP_IMMEDIATE;
    }
}
```

---

### AgentSignal (mis a jour)

```java
package com.performance.platform.domain.event;

import com.performance.platform.domain.id.SignalId;

import java.time.Instant;

/**
 * Interface scellee pour les signaux broadcast de l'orchestrateur vers les agents.
 * <p>
 * Tous les signaux sont transmis via {@code ExecutionTransport.broadcastSignal()}
 * et recus par les agents via {@code ExecutionTransport.receiveSignal()}.
 * <p>
 * Permits mis a jour (PDR-038) : ajout de {@link ExecutionLifecycleSignal}.
 */
public sealed interface AgentSignal
        permits ScenarioRestartSignal, ExecutionLifecycleSignal {
    SignalId id();
    Instant issuedAt();
}
```

---

## Regles de Comportement

- **Generaliste** : `ExecutionLifecycleSignal` est envoye pour TOUTES les tasks, pas seulement les assertions. START avant chaque execution, STOP apres.
- **Assertions avec `linkedTo`** : START est envoye avant l'injection liee, STOP apres la completion de l'injection. La fenetre entre START et STOP est la periode de monitoring.
- **Tasks standards** : START et STOP encadrent immediatement l'execution de la task. L'agent recoit les deux signaux en sequence rapide.
- **Silent ignore** : Un agent qui n'est pas specialise pour le `taskId` du signal ignore le signal (log DEBUG). Pas d'erreur.
- **Idempotence** : Un agent ne doit pas re-demarrer une boucle de sampling si un signal START est recu pour une task deja en cours. Verifier via `taskId` + `executionId`.
- **Signal STOP toujours envoye** : Meme si la task echoue, le STOP est envoye pour nettoyer les ressources.
- **Parameters** : Les parametres YAML (`stopBehavior`, `gracePeriodDuration`) sont lus par l'engine depuis `StepDefinition.parameters()` et transmis dans le signal. L'agent les lit depuis le signal, pas depuis `step.parameters()`.
- **Serialisation** : Le record est serialisable par Jackson sans annotations. `LifecycleAction` enum est serialise comme String via `Enum.name()`.
- **Transport** : `ExecutionLifecycleSignal` est automatiquement transportable via `transport.broadcastSignal()` car il implemente `AgentSignal`. Aucune modification des 5 implementations de transport n'est necessaire.
- **SignalId** : Utilise le value object `SignalId` existant (PDR-001). Factory `SignalId.generate()` pour generer un UUID.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-001 (platform-domain records)  -> SignalId, ExecutionId, TaskId (deja STABLE)
  PDR-002 (Domain Events)            -> AgentSignal sealed interface (deja STABLE)

Ce PDR est utilise par :
  PDR-037 (engine lifecycle dispatch) -> engine envoie ExecutionLifecycleSignal
  platform-agent-runtime               -> agents recoivent et traitent ExecutionLifecycleSignal
  platform-transport (toutes les 5 implementations) -> transportent automatiquement via AgentSignal
```

---

## Criteres de Done (PDR complet)

- [ ] `LifecycleAction` enum compile dans `platform-domain` (0 erreur)
- [ ] `ExecutionLifecycleSignal` record compile dans `platform-domain` (0 erreur)
- [ ] `AgentSignal` sealed interface `permits` mis a jour : ajout de `ExecutionLifecycleSignal`
- [ ] `ScenarioRestartSignal` compile toujours (pas de breaking change)
- [ ] ArchUnit test : 0 annotation framework dans le package `event`
- [ ] Tests unitaires : constructeur compact avec null checks
- [ ] Tests unitaires : `stopBehavior()` avec toutes les valeurs valides et invalides
- [ ] Tests unitaires : factories `start()` et `stop()`
- [ ] Tests unitaires : `Map.copyOf()` defensive copy dans le constructeur compact
- [ ] Les nouveaux types sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
