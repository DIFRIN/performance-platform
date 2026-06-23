# ISSUE-120: Use cases ListExecutions/DeleteExecution + ExecutionProgress serveur

**PDR** : PDR-027
**Module** : `platform-application`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-119
**Taille** : M
**Estime** : M

---

## Objectif

Creer les use cases `ListExecutionsUseCase` et `DeleteExecutionUseCase` (ports in + implementations applicatives),
le record domaine `ExecutionProgress`, et la logique de calcul de progression cote serveur a partir d'un
`ExecutionState`. Le Developer peut verifier que la progression `{total, ok, ko, running}` est derivee du
statut reel des tasks.

---

## Fichiers à Créer / Modifier

```
platform-domain/src/main/java/com/performance/platform/domain/execution/
  └── ExecutionProgress.java           — record pur {total, ok, ko, running} (0 annotation)

platform-application/src/main/java/com/performance/platform/application/ports/in/
  ├── ListExecutionsUseCase.java       — port in
  └── DeleteExecutionUseCase.java      — port in

platform-application/src/main/java/com/performance/platform/application/usecase/
  ├── ListExecutionsService.java       — impl (delegue ExecutionRepository.findAll)
  ├── DeleteExecutionService.java      — impl (garde de statut + delegue ExecutionRepository.deleteById)
  ├── ExecutionProgressCalculator.java — derive ExecutionProgress (state + taskResults — ADR-020)
  └── ExecutionNotDeletableException.java — levee si delete sur execution active (ADR-020)

platform-application/src/test/java/com/performance/platform/application/usecase/
  ├── ListExecutionsServiceTest.java
  ├── DeleteExecutionServiceTest.java
  └── ExecutionProgressCalculatorTest.java
```

---

## Interfaces à Implémenter

```java
// platform-domain
public record ExecutionProgress(int total, int ok, int ko, int running) {
    public ExecutionProgress {
        if (total < 0 || ok < 0 || ko < 0 || running < 0) {
            throw new IllegalArgumentException("progress counters must be >= 0");
        }
    }
}

// platform-application — ports in
public interface ListExecutionsUseCase {
    List<ExecutionState> list(int limit);   // defaut applique si limit <= 0
}

public interface DeleteExecutionUseCase {
    void delete(ExecutionId id);
}
```

---

## Règles Spécifiques

- `ExecutionProgress` est dans `platform-domain` : record pur, 0 annotation framework (verifie par ArchUnit existant).
- `ListExecutionsService.list(limit)` : si `limit <= 0`, appliquer un defaut (ex: 50) plutot qu'echouer.
- **CORRECTION ARCHITECT (ADR-020)** : `ExecutionState` ne contient PAS les resultats par task — la
  progression NE PEUT PAS etre derivee d'un `ExecutionState` seul. `ExecutionProgressCalculator` recoit
  AUSSI les `taskResults` (lus via `ExecutionRepository.getTaskResults`). Signature :
  `ExecutionProgress calculate(ExecutionState state, Map<TaskId, Map<AgentId, TaskResult>> taskResults)`.
  `ok`/`ko` derives du `TaskStatus` reel des `TaskResult` ; `running` = tasks planifiees sans resultat terminal.
- **CORRECTION ARCHITECT (ADR-020)** : `DeleteExecutionService.delete(id)` leve `ExecutionNotDeletableException`
  si le statut est `STARTED` ou `RUNNING` (suppression interdite sur execution active — protege le checkpointing
  CNF-02). Suppression autorisee uniquement pour `COMPLETED | FAILED | CANCELLED`.
- Les impls applicatives peuvent porter les annotations Spring (`@Service`) — c'est `platform-domain` qui doit rester pur.

---

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur, 0 warning
- [ ] `ExecutionProgress` cree dans platform-domain (ArchUnit domaine OK, 0 annotation)
- [ ] `list(limit)` applique un defaut si limit <= 0, delegue findAll
- [ ] `delete(id)` leve ExecutionNotDeletableException si STARTED/RUNNING ; sinon delegue deleteById (ADR-020)
- [ ] Calcul de progression derive du statut reel des tasks via (state + taskResults) (tests couvrant ok/ko/running)
- [ ] `.claude/workspace/progress.md` : ISSUE-120 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ExecutionProgress, ListExecutionsUseCase, DeleteExecutionUseCase)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel
