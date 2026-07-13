# ISSUE-163 — Sealed Event Interfaces (ExecutionEvent, TaskEvent, AssertionEvent)

**PDR** : PDR-040
**Module** : `platform-domain`
**Statut** : WAITING
**Priorite** : P2 (documentation/structure, pas de changement de comportement)
**Bloquee par** : —
**Estime** : M (1-2h)

---

## Objectif

Créer 3 interfaces scellées (`ExecutionEvent`, `TaskEvent`, `AssertionEvent`) et modifier les 16 records d'événements existants pour les implémenter. Aucun changement de comportement — les records conservent leurs composants et constructeurs.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  ├── ExecutionEvent.java          — NOUVEAU: sealed interface
  ├── TaskEvent.java               — NOUVEAU: sealed interface
  └── AssertionEvent.java          — NOUVEAU: sealed interface
```

## Fichiers à Modifier (16 records)

Chaque record reçoit `implements XxxEvent` :

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  ├── ScenarioStarted.java         — ajouter implements ExecutionEvent
  ├── ScenarioFinished.java        — ajouter implements ExecutionEvent
  ├── ScenarioCancelled.java       — ajouter implements ExecutionEvent
  ├── PhaseStarted.java            — ajouter implements ExecutionEvent
  ├── PhaseCompleted.java          — ajouter implements ExecutionEvent
  ├── ReportGenerated.java         — ajouter implements ExecutionEvent
  ├── ReportPublished.java         — ajouter implements ExecutionEvent
  ├── TaskStarted.java             — ajouter implements TaskEvent
  ├── TaskCompleted.java           — ajouter implements TaskEvent
  ├── TaskFailed.java              — ajouter implements TaskEvent
  ├── TaskRetried.java             — ajouter implements TaskEvent
  ├── TaskDispatched.java          — ajouter implements TaskEvent
  ├── TaskClaimedByAgent.java      — ajouter implements TaskEvent
  ├── TaskWorkInProgress.java      — ajouter implements TaskEvent
  ├── AssertionPassed.java         — ajouter implements AssertionEvent
  └── AssertionFailed.java         — ajouter implements AssertionEvent
```

## Interfaces

### ExecutionEvent

```java
package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;

import java.time.Instant;

/**
 * Événement lié au cycle de vie d'une exécution de scénario.
 */
public sealed interface ExecutionEvent
        permits ScenarioStarted, ScenarioFinished, ScenarioCancelled,
                PhaseStarted, PhaseCompleted,
                ReportGenerated, ReportPublished {

    ExecutionId executionId();
    Instant timestamp();
}
```

### TaskEvent

```java
package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;

/**
 * Événement lié au cycle de vie d'une tâche.
 */
public sealed interface TaskEvent
        permits TaskStarted, TaskCompleted, TaskFailed, TaskRetried,
                TaskDispatched, TaskClaimedByAgent, TaskWorkInProgress {

    ExecutionId executionId();
    TaskId taskId();
    Instant timestamp();
}
```

### AssertionEvent

```java
package com.performance.platform.domain.event;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;

/**
 * Événement de résultat d'assertion.
 */
public sealed interface AssertionEvent
        permits AssertionPassed, AssertionFailed {

    ExecutionId executionId();
    TaskId taskId();
    AssertionResult result();
    Instant timestamp();
}
```

## Vérification de compatibilité

Avant modification, vérifier que chaque record a bien les accesseurs requis par l'interface :
- `ExecutionEvent` : `executionId()`, `timestamp()` — tous les 7 records ont ces champs
- `TaskEvent` : `executionId()`, `taskId()`, `timestamp()` — tous les 7 records ont ces champs
- `AssertionEvent` : `executionId()`, `taskId()`, `result()`, `timestamp()` — les 2 records ont ces champs

## Règles Spécifiques

- `AgentSignal` est DÉJÀ `sealed permits ScenarioRestartSignal` — pas de changement
- `ScenarioRestartSignal` est déjà `implements AgentSignal` — pas de changement
- Aucune annotation Spring/JPA/Jackson sur les interfaces (règle domaine)
- `AssertionResult` est déjà un record dans `domain/assertion/` — pas de changement
- Les interfaces sont dans le même package `event` que les records — pas de problème de visibilité

## Criteres de Done

- [ ] `ExecutionEvent` sealed interface créée avec 7 permits
- [ ] `TaskEvent` sealed interface créée avec 7 permits
- [ ] `AssertionEvent` sealed interface créée avec 2 permits
- [ ] 16 records modifiés avec `implements XxxEvent`
- [ ] `mvn compile -pl platform-domain -q` → 0 erreur
- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] Aucun test modifié (les interfaces sont additives)
