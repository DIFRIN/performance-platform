# PDR-040 — Event Package Formalization

**Module Maven** : `platform-domain`
**Package** : `com.performance.platform.domain.event`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/02-execution-engine.md`, ADR-012
**Depend de** : PDR-002 (Events — déjà DONE)
**Issues** : ISSUE-163, ISSUE-164

---

## Responsabilite

Formaliser la catégorisation des événements du domaine avec des interfaces scellées (`sealed interface`) et de la documentation (`package-info.java`). Les événements sont déjà regroupés par thématique mais cette organisation est implicite. Ce PDR la rend explicite et vérifiable par le compilateur.

**Aucun changement de comportement.** Les records existants conservent leurs composants, leurs constructeurs, et leur package. L'ajout d'interfaces scellées est purement additif.

---

## Catégories d'Événements

### 1. ExecutionEvent — cycle de vie du scénario

```java
public sealed interface ExecutionEvent
        permits ScenarioStarted, ScenarioFinished, ScenarioCancelled,
                PhaseStarted, PhaseCompleted,
                ReportGenerated, ReportPublished {

    ExecutionId executionId();
    Instant timestamp();
}
```

Events concernés (7 records) :
- `ScenarioStarted` — début d'exécution d'un scénario
- `ScenarioFinished` — fin d'exécution (avec verdict)
- `ScenarioCancelled` — annulation
- `PhaseStarted` — début d'une phase (PREPARATION/INJECTION/ASSERTION)
- `PhaseCompleted` — fin d'une phase (avec statut)
- `ReportGenerated` — rapport généré
- `ReportPublished` — rapport publié

### 2. TaskEvent — cycle de vie d'une tâche

```java
public sealed interface TaskEvent
        permits TaskStarted, TaskCompleted, TaskFailed, TaskRetried,
                TaskDispatched, TaskClaimedByAgent, TaskWorkInProgress {

    ExecutionId executionId();
    TaskId taskId();
    Instant timestamp();
}
```

Events concernés (7 records) :
- `TaskStarted` — début d'exécution (obsolète depuis ADR-011 mais conservé)
- `TaskCompleted` — succès (avec TaskResult)
- `TaskFailed` — échec (avec erreur, attempt)
- `TaskRetried` — retry déclenché
- `TaskDispatched` — tâche diffusée aux agents
- `TaskClaimedByAgent` — agent a réclamé la tâche
- `TaskWorkInProgress` — heartbeat de progression

### 3. AssertionEvent — résultat d'assertion

```java
public sealed interface AssertionEvent
        permits AssertionPassed, AssertionFailed {

    ExecutionId executionId();
    TaskId taskId();
    AssertionResult result();
    Instant timestamp();
}
```

Events concernés (2 records) :
- `AssertionPassed` — assertion vérifiée avec succès
- `AssertionFailed` — assertion en échec

### 4. AgentSignal — inchangé (déjà sealed)

```java
// Existant — déjà sealed, déjà dans le domaine
public sealed interface AgentSignal permits ScenarioRestartSignal {
    SignalId id();
    Instant issuedAt();
}
// ExecutionLifecycleSignal sera ajouté via PDR-038
```

### Hors scope (transport layer — ADR-012)

Les événements de lifecycle agent sont dans `platform-transport`, pas dans le domaine :
- `AgentRegistered`, `AgentLost`, `AgentRecovered` → `AgentLifecycleEvent` (déjà séparé par ADR-012)

---

## package-info.java

Fichier à créer : `platform-domain/src/main/java/com/performance/platform/domain/event/package-info.java`

```java
/**
 * Domain events — immuables, 0-framework.
 *
 * <h2>Catégories</h2>
 * <table>
 *   <tr><th>Interface</th><th>Périmètre</th><th>Events</th></tr>
 *   <tr><td>{@code ExecutionEvent} (sealed)</td><td>Cycle de vie du scénario</td>
 *       <td>ScenarioStarted, ScenarioFinished, ScenarioCancelled,
 *           PhaseStarted, PhaseCompleted, ReportGenerated, ReportPublished</td></tr>
 *   <tr><td>{@code TaskEvent} (sealed)</td><td>Cycle de vie d'une tâche</td>
 *       <td>TaskStarted, TaskCompleted, TaskFailed, TaskRetried,
 *           TaskDispatched, TaskClaimedByAgent, TaskWorkInProgress</td></tr>
 *   <tr><td>{@code AssertionEvent} (sealed)</td><td>Résultat d'assertion</td>
 *       <td>AssertionPassed, AssertionFailed</td></tr>
 *   <tr><td>{@code AgentSignal} (sealed)</td><td>Signaux orchestrateur → agents</td>
 *       <td>ScenarioRestartSignal</td></tr>
 * </table>
 *
 * <h2>Hors-domaine (transport layer)</h2>
 * Les événements de cycle de vie agent ({@code AgentRegistered}, {@code AgentLost},
 * {@code AgentRecovered}) sont dans {@code platform-transport}, pas ici.
 * Voir ADR-012.
 */
package com.performance.platform.domain.event;
```

---

## Modifications par Record

Chaque record existant reçoit `implements XxxEvent` :

| Record | Ajout |
|---|---|
| `ScenarioStarted` | `implements ExecutionEvent` |
| `ScenarioFinished` | `implements ExecutionEvent` |
| `ScenarioCancelled` | `implements ExecutionEvent` |
| `PhaseStarted` | `implements ExecutionEvent` |
| `PhaseCompleted` | `implements ExecutionEvent` |
| `ReportGenerated` | `implements ExecutionEvent` |
| `ReportPublished` | `implements ExecutionEvent` |
| `TaskStarted` | `implements TaskEvent` |
| `TaskCompleted` | `implements TaskEvent` |
| `TaskFailed` | `implements TaskEvent` |
| `TaskRetried` | `implements TaskEvent` |
| `TaskDispatched` | `implements TaskEvent` |
| `TaskClaimedByAgent` | `implements TaskEvent` |
| `TaskWorkInProgress` | `implements TaskEvent` |
| `AssertionPassed` | `implements AssertionEvent` |
| `AssertionFailed` | `implements AssertionEvent` |

**Note** : `AgentSignal` est déjà `sealed permits ScenarioRestartSignal`. Pas de changement.
**Note** : `ScenarioRestartSignal` est déjà `implements AgentSignal`. Pas de changement.

---

## Vérification ArchUnit (ISSUE-164)

Ajouter un test ArchUnit qui vérifie que :
1. Tous les records du package `event` implémentent exactement une des 4 interfaces scellées
2. Aucun record hors `event` n'implémente `ExecutionEvent`, `TaskEvent`, `AssertionEvent`, ou `AgentSignal`
3. Les interfaces scellées `permits` sont exhaustives (tous les records listés existent)

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-002 (Events) → déjà DONE

Ce PDR est utilisé par :
  (aucun — documentation/structure)
```

---

## Criteres de Done (PDR complet)

- [ ] `ExecutionEvent` sealed interface créée (ISSUE-163)
- [ ] `TaskEvent` sealed interface créée (ISSUE-163)
- [ ] `AssertionEvent` sealed interface créée (ISSUE-163)
- [ ] 16 records modifiés avec `implements XxxEvent` (ISSUE-163)
- [ ] `package-info.java` créé avec la documentation des catégories (ISSUE-164)
- [ ] Test ArchUnit de vérification des hiérarchies scellées (ISSUE-164)
- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] Aucun changement de comportement — les events sont toujours des records immuables
