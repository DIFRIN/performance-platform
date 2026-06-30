# ISSUE-146 — GetExecutionStatusService : read-model framework-free (platform-application)

**PDR** : PDR-033
**Module** : `platform-application`
**Statut** : DONE
**Priorité** : P1 (critique — fournit l'implémentation du port de statut, découplée de l'engine)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Créer un service **framework-free** `GetExecutionStatusService` implémentant
`GetExecutionStatusUseCase`, qui lit le statut/état d'une exécution **uniquement depuis
`ExecutionRepository`** (read-model, CQRS-lean — ADR-026). Décorrèle la lecture de statut de
l'engine (commande). Cohérent avec `ListExecutionsService`/`DeleteExecutionService` existants.

## Fichiers à Créer / Modifier

```
CRÉER (main) :
platform-application/src/main/java/com/performance/platform/application/usecase/
  └── GetExecutionStatusService.java   — implements GetExecutionStatusUseCase (0 annotation Spring)

CRÉER (test) :
platform-application/src/test/java/com/performance/platform/application/usecase/
  └── GetExecutionStatusServiceTest.java   — repository mocké (Mockito), cas présent/absent
```

## Interfaces à Implémenter

```java
// Port in existant (inchangé)
public interface GetExecutionStatusUseCase {
    ExecutionStatus getStatus(ExecutionId id);
    Optional<ExecutionState> getState(ExecutionId id);
}

// Nouveau service — 0 annotation Spring (platform-application reste framework-free)
public final class GetExecutionStatusService implements GetExecutionStatusUseCase {

    private final ExecutionRepository repository;

    public GetExecutionStatusService(ExecutionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository required");
    }

    @Override
    public ExecutionStatus getStatus(ExecutionId id) {
        return repository.findById(id)
                .map(ExecutionState::status)
                .orElse(ExecutionStatus.STARTED);   // défaut cohérent avec l'usage existant (cf. E2E)
    }

    @Override
    public Optional<ExecutionState> getState(ExecutionId id) {
        return repository.findById(id);
    }
}
```

## Règles Spécifiques

- **0 annotation Spring** : `platform-application` est framework-free (inviolable). Le bean est
  câblé par la racine `platform-app` (ISSUE-144).
- Le défaut de `getStatus` quand l'état n'est pas (encore) persisté : retourner
  `ExecutionStatus.STARTED` (aligné sur le comportement du `GetExecutionStatusUseCase` anonyme
  des tests E2E actuels — vérifier la valeur attendue).
- Pas de dépendance à l'engine ni au transport : uniquement `ExecutionRepository`.
- Tests unitaires avec `ExecutionRepository` mocké (Mockito) : état présent → statut/état
  correct ; absent → `Optional.empty()` et statut par défaut.

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur
- [ ] `GetExecutionStatusService` créé, **0 annotation Spring**
- [ ] Tests : cas présent (statut + état) et absent (`Optional.empty()`, statut défaut)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : `GetExecutionStatusService` ajouté (read-model)
</content>
