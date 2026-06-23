# ISSUE-121: Endpoints REST executions (list / tasks / delete) + DTOs + progress

**PDR** : PDR-027
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-120
**Taille** : M
**Estime** : M

---

## Objectif

Exposer `GET /api/v1/executions?limit=N`, `GET /api/v1/executions/{id}/tasks`,
`DELETE /api/v1/executions/{id}`, et enrichir la reponse de status existante avec le champ `progress`.
Le Developer peut verifier via MockMvc que list retourne des resumes avec progression, tasks retourne
des resumes pagines, et delete retourne 204.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/app/api/
  ├── ExecutionController.java          — nouveau controller IHM (list/tasks/delete)
  └── ScenarioController.java           — MODIF : embarquer progress dans getStatus

platform-app/src/main/java/com/performance/platform/app/api/dto/
  ├── ExecutionSummaryResponse.java
  ├── ProgressResponse.java
  ├── TaskSummaryResponse.java
  ├── TaskListResponse.java
  └── ExecutionStatusResponse.java      — MODIF : ajout champ progress

platform-app/src/test/java/com/performance/platform/app/api/
  └── ExecutionControllerTest.java      — @WebMvcTest (list/tasks/delete/status+progress)
```

---

## Interfaces à Implémenter

```java
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {
    @GetMapping("/executions")                 // ?limit=N → List<ExecutionSummaryResponse>
    @GetMapping("/executions/{id}/tasks")      // → TaskListResponse (resumes)
    @DeleteMapping("/executions/{id}")         // → 204 No Content
}

public record ExecutionSummaryResponse(
    String executionId, String scenarioId, String status,
    String startedAt, String updatedAt, ProgressResponse progress) {}

public record ProgressResponse(int total, int ok, int ko, int running) {}

public record TaskSummaryResponse(
    String taskId, String taskName, String phase, String status, String errorMessage) {}

public record TaskListResponse(String executionId, int total, List<TaskSummaryResponse> tasks) {}
```

---

## Règles Spécifiques

- `GET /executions?limit=N` : delegue `ListExecutionsUseCase`, mappe chaque `ExecutionState` vers `ExecutionSummaryResponse` avec `progress` calcule serveur.
- `GET /executions/{id}/tasks` : retourne des resumes. `errorMessage` present uniquement si KO. Liste paginee/resumee.
- `DELETE /executions/{id}` : delegue `DeleteExecutionUseCase`, retourne `204` si terminee.
  **CORRECTION ARCHITECT (ADR-020)** : si l'execution est active (`STARTED`/`RUNNING`),
  `DeleteExecutionUseCase` leve `ExecutionNotDeletableException` → mapper en `409 Conflict`
  dans `ApiExceptionHandler`. L'IHM doit `cancel` avant `delete`.
- `getStatus` (ScenarioController existant) : embarquer `progress` (ProgressResponse) dans `ExecutionStatusResponse`.
- Reutiliser `ExecutionProgressCalculator` (ISSUE-120) en lui passant `state` + `taskResults`
  (lus via le repository). Ne PAS calculer la progression cote controller, ne PAS la deriver d'un `ExecutionState` seul.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `GET /executions?limit=N` → liste de resumes triee desc avec progress
- [ ] `GET /executions/{id}/tasks` → resumes (errorMessage si KO)
- [ ] `DELETE /executions/{id}` → 204 si terminee ; 409 si active (STARTED/RUNNING) — ADR-020
- [ ] `GET /executions/{id}` (status) embarque progress (calcule depuis state + taskResults)
- [ ] `.claude/workspace/progress.md` : ISSUE-121 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (DTOs IHM)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel
