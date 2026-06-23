# PDR-027 — IHM Backend API Extensions

**Module Maven** : `platform-application` (ports + use cases), `platform-infrastructure` (JPA adapter), `platform-app` (REST controllers + DTOs)
**Package** : `com.performance.platform.application.ports`, `com.performance.platform.infrastructure.persistence`, `com.performance.platform.app.api`
**Statut** : WAITING
**Specs de reference** : Design valide utilisateur (Web IHM), `.claude/knowledge/specs/02-execution-engine.md`, `.claude/knowledge/specs/04-agent-runtime.md`, `.claude/knowledge/specs/08-report-engine.md`, ADR-013 (Spring-first infra)
**Depend de** : PDR-004 (Application Ports & Use Cases — DONE), PDR-012 (Persistence — DONE), PDR-018 (Application Assembly — DONE), PDR-015 (Reporting Engine — DONE)
**Issues** : ISSUE-119, ISSUE-120, ISSUE-121, ISSUE-122, ISSUE-123, ISSUE-124

---

## Responsabilite

Ce PDR construit toutes les extensions backend necessaires a l'IHM Web : nouveaux ports
sortants, nouveaux use cases, nouveaux endpoints REST sous `/api/v1/**`, et les DTOs associes.
L'IHM est une simple couche de consommation au-dessus de cette API : tout le calcul (progression,
agregation, listing) est fait cote serveur.

Constats verifies dans le code :
- `ExecutionRepository` n'a PAS de `findAll()` ni de `deleteById` → extension du port + adapter JPA.
- `GetExecutionStatusUseCase` n'a pas de methode de listing → nouveaux use cases.
- `AgentRegistryPort.findAll()` existe deja → l'endpoint agents est du cablage uniquement.
- `ReportFileWriter` ecrit dans un repertoire configure sur le filesystem local → le controller
  streame le fichier deja genere (co-localisation assumee en v1).

**Ce que ce PDR fait** :
1. Etend `ExecutionRepository` : `findAll(int limit)`, `deleteById(ExecutionId)`.
2. Cree `ListExecutionsUseCase` et `DeleteExecutionUseCase`.
3. Enrichit la reponse de statut avec une progression `{total, ok, ko, running}` calculee cote serveur.
4. Expose les endpoints REST : list executions, tasks d'une execution, delete, agents, report (stream), upload scenario.
5. Aligne le DTO d'erreur de validation sur un format structure (champ + message).

**Ce que ce PDR NE fait PAS** :
- Ne genere PAS le rapport sur demande : le rapport est genere AUTOMATIQUEMENT en fin d'execution
  (lifecycle, voir PDR-015). Le controller ne fait que streamer le fichier deja produit.
- Ne sert PAS de catalogue de scenarios : l'upload execute immediatement.
- Ne sert PAS les ressources statiques de l'IHM (→ PDR-028).
- N'introduit PAS de SSE/WebSocket : le temps reel est gere par polling cote client (→ PDR-029).
- N'ajoute aucun nouveau module Maven.

---

## Interfaces Publiques

### Extension de ExecutionRepository (port sortant — `platform-application`)

```java
package com.performance.platform.application.ports.out;

public interface ExecutionRepository {
    // ... methodes existantes inchangees (save, findById, updatePhase, saveTaskResult, getTaskResults) ...

    /**
     * Retourne les executions les plus recentes, triees par startedAt desc.
     *
     * @param limit nombre maximal d'executions a retourner (> 0)
     * @return liste des etats d'execution, possiblement vide
     */
    List<ExecutionState> findAll(int limit);

    /**
     * Supprime une execution et tous ses resultats de tache.
     * No-op si l'execution n'existe pas.
     *
     * @param id l'identifiant de l'execution a supprimer
     */
    void deleteById(ExecutionId id);
}
```

### ListExecutionsUseCase (port entrant — `platform-application`)

```java
package com.performance.platform.application.ports.in;

import com.performance.platform.domain.execution.ExecutionState;
import java.util.List;

/**
 * Use case : lister les executions recentes pour l'IHM.
 */
public interface ListExecutionsUseCase {

    /**
     * @param limit nombre maximal d'executions (borne cote impl, defaut applique si <= 0)
     * @return executions triees par startedAt desc
     */
    List<ExecutionState> list(int limit);
}
```

### DeleteExecutionUseCase (port entrant — `platform-application`)

```java
package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ExecutionId;

/**
 * Use case : supprimer une execution (et ses resultats).
 */
public interface DeleteExecutionUseCase {

    void delete(ExecutionId id);
}
```

### ExecutionProgress (record domaine de progression — calcule cote serveur)

```java
package com.performance.platform.domain.execution;

/**
 * Agregat de progression d'une execution, calcule cote serveur.
 * Immuable. 0 annotation framework.
 *
 * @param total   nombre total de tasks planifiees
 * @param ok      tasks terminees avec succes
 * @param ko      tasks terminees en echec
 * @param running tasks en cours d'execution
 */
public record ExecutionProgress(int total, int ok, int ko, int running) {
    public ExecutionProgress {
        if (total < 0 || ok < 0 || ko < 0 || running < 0) {
            throw new IllegalArgumentException("progress counters must be >= 0");
        }
    }
}
```

> Note : `ExecutionProgress` est un record domaine pur (0 annotation), donc place dans `platform-domain`.
> Le calcul (mapping ExecutionState → ExecutionProgress) est realise dans la couche application.

### DTOs REST (`platform-app` — package `com.performance.platform.app.api.dto`)

```java
// Resume d'execution pour la liste
public record ExecutionSummaryResponse(
    String executionId,
    String scenarioId,
    String status,            // STARTED | RUNNING | COMPLETED | FAILED | CANCELLED
    String startedAt,         // ISO-8601, nullable
    String updatedAt,         // ISO-8601, nullable
    ProgressResponse progress
) {}

// Progression embarquee dans status + summary
public record ProgressResponse(int total, int ok, int ko, int running) {}

// Detail d'une task pour la vue detail (resume; detail complet a la demande)
public record TaskSummaryResponse(
    String taskId,
    String taskName,
    String phase,             // PREPARATION | INJECTION | ASSERTION ...
    String status,            // OK | KO | RUNNING
    String errorMessage       // nullable — present uniquement si KO
) {}

// Liste paginee/resumee des tasks
public record TaskListResponse(
    String executionId,
    int total,
    java.util.List<TaskSummaryResponse> tasks
) {}

// Agent pour le dashboard ORCHESTRATOR
public record AgentResponse(
    String agentId,
    String name,
    String state,
    java.util.Set<String> supportedTasks,
    String lastHeartbeatAt    // ISO-8601, nullable
) {}

// Erreur de validation field-level (upload + submit)
public record ValidationErrorResponse(
    String message,
    java.util.List<FieldError> errors
) {
    public record FieldError(String field, String message) {}
}
```

> Le `ExecutionStatusResponse` existant (ISSUE-079) est etendu pour embarquer un champ `progress`
> de type `ProgressResponse`. Voir ISSUE-120 et ISSUE-121.

---

## Regles de Comportement

- **`findAll(limit)`** : tri par `startedAt` decroissant. La borne `limit` est appliquee dans la requete JPA
  (pas en memoire). `limit <= 0` → exception applicative `IllegalArgumentException` au niveau use case, qui
  applique un defaut raisonnable (ex: 50) plutot que d'echouer.
- **`deleteById`** : transactionnel. Supprime l'execution et ses resultats de tache (cascade JPA ou suppression explicite). No-op silencieux si absent.
- **Progression** : `ExecutionProgress` est derive de `ExecutionState` cote application — JAMAIS cote client.
  `running = total - ok - ko` n'est pas calcule par soustraction naive : il est derive du statut reel des tasks.
- **Endpoint report** (`GET /executions/{id}/report?format=html|pdf|json`) : streame le fichier DEJA genere
  depuis le repertoire de `ReportFileWriter`. Si le fichier n'existe pas encore → `404 Not Found` (l'IHM poll).
  Le `Content-Type` est fixe selon `format` (`text/html`, `application/pdf`, `application/json`).
  Le controller NE declenche JAMAIS la generation.
- **Endpoint agents** (`GET /agents`) : conditionnel ORCHESTRATOR uniquement (`@ConditionalOnProperty` ou garde mode).
  En LOCAL, l'endpoint peut retourner une liste vide ou ne pas etre monte. Wire `AgentRegistryPort.findAll()`.
- **Upload scenario** (`POST /scenarios/upload`) : `multipart/form-data` (fichier) OU contenu YAML texte (champ form).
  Valide via `ScenarioParsingUseCase` + `ScenarioValidator`. Invalide → `400` avec `ValidationErrorResponse`
  (liste champ+message). Valide → execute immediatement via `ExecuteScenarioUseCase`, retourne `202` + executionId.
- **Cancel / delete** : `POST /executions/{id}/cancel` (existant, ISSUE-079) ; `DELETE /executions/{id}` (nouveau).
- **Pagination tasks** : `GET /executions/{id}/tasks` retourne des resumes. Le detail complet d'une task est servi
  a la demande (meme endpoint, ou enrichissement v1 minimal : resume suffit, detail = champs deja presents).
- **Erreurs** : `ScenarioValidationException` → 400 `ValidationErrorResponse` ; execution introuvable → 404 ;
  `NoAvailableAgentException` → 503. Aligner `ApiExceptionHandler` existant.
- **Aucune annotation Spring/JPA dans `platform-domain`** : `ExecutionProgress` reste un record pur.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-001 (Domain Core)            → ExecutionState, ExecutionStatus, ExecutionId, TaskResult, ExecutionProgress (nouveau)
  PDR-004 (Application Ports)      → ExecutionRepository (etendu), GetExecutionStatusUseCase, ExecuteScenarioUseCase,
                                     CancelExecutionUseCase, ScenarioParsingUseCase
  PDR-012 (Persistence)            → JpaExecutionRepository (etendu : findAll, deleteById)
  PDR-015 (Reporting)              → ReportFileWriter (repertoire de sortie pour le stream)
  PDR-018 (Application Assembly)   → ScenarioController, ApiExceptionHandler, DTOs existants
  AgentRegistryPort.findAll()      → deja existant, cablage pour l'endpoint agents

Ce PDR est utilise par :
  PDR-028 (IHM Web Serving)        → l'IHM est servie a cote de cette API
  PDR-029 (IHM Frontend Views)     → les vues JS consomment ces endpoints
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues ISSUE-119 a ISSUE-124 sont DONE
- [ ] `ExecutionRepository.findAll(int)` et `deleteById(ExecutionId)` implementes dans `JpaExecutionRepository`
- [ ] `ListExecutionsUseCase`, `DeleteExecutionUseCase`, `ExecutionProgress` dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] `mvn test -pl platform-application -q` et `mvn test -pl platform-infrastructure -q` passent
- [ ] `mvn test -pl platform-app -q` passe (MockMvc sur tous les nouveaux endpoints)
- [ ] Endpoint report streame le fichier deja genere (404 si absent, jamais de generation)
- [ ] Endpoint agents conditionnel ORCHESTRATOR, wire `AgentRegistryPort.findAll()`
- [ ] DTO d'erreur de validation aligne sur le format champ+message
- [ ] ArchUnit : `platform-domain` toujours 0 annotation framework (ExecutionProgress pur)
