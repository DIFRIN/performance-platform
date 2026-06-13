# ISSUE-044 — DockerTaskExecutor

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-039
**Estime** : M

---

## Objectif

Implémenter `DockerTaskExecutor` : START/STOP/PULL de containers avec ports, env, health check.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/docker/
  └── DockerTaskExecutor.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/docker/
  └── DockerTaskExecutorTest.java — mock du client Docker (pas de daemon en test unitaire)
```

## Interfaces à Implémenter

```java
@Preparation(name = "docker", description = "Docker start/stop/pull")
public class DockerTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "docker"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `action` (START/STOP/PULL), `image`, `containerName`, `ports`, `env`, `waitForHealthCheck`, `healthCheckTimeout`.
- Outputs : `{ "containerId": "...", "status": "running" }`.
- Implémente `StatefulResourceCleaner` (stop des containers démarrés par cette exécution).
- Échec → `TaskResult.failed`.
- Tests unitaires : mocker le client Docker (pas de daemon requis).

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] START (mock) → outputs containerId
- [ ] cleanup arrête les containers tracés
- [ ] `.claude/progress.md` mis à jour : ISSUE-044 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `DockerTaskExecutor` → STABLE
