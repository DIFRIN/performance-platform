# ISSUE-042 — MockServerTaskExecutor (WireMock embedded/external)

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-039
**Estime** : M

---

## Objectif

Implémenter `MockServerTaskExecutor` : démarre/arrête/reset un WireMock embedded ou pilote un
WireMock externe.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/mock/
  └── MockServerTaskExecutor.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/mock/
  └── MockServerTaskExecutorTest.java — START embedded → outputs port/url
```

## Interfaces à Implémenter

```java
@Preparation(name = "mock-server", description = "WireMock embedded/external")
public class MockServerTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "mock-server"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `deployment` (EMBEDDED/EXTERNAL), `port`, `mappingsPath`, `action` (START/STOP/RESET/VERIFY), `externalUrl`.
- Outputs : `{ "port": 8090, "url": "http://localhost:8090" }`.
- Implémente `StatefulResourceCleaner` (stop WireMock, reset mappings).
- Échec → `TaskResult.failed`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] START embedded → outputs `port` et `url`
- [ ] cleanup arrête le serveur
- [ ] `.claude/progress.md` mis à jour : ISSUE-042 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `MockServerTaskExecutor` → STABLE
