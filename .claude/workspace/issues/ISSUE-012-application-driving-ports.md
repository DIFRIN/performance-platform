# ISSUE-012 — Ports entrants (use cases) + exceptions applicatives

**PDR** : PDR-004
**Module** : `platform-application`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-003, ISSUE-006
**Estime** : M

---

## Objectif

Créer le module `platform-application` (dépend de `platform-domain` uniquement) et les
ports entrants (use cases) + exceptions applicatives.

## Fichiers à Créer

```
platform-application/pom.xml — dépend de platform-domain uniquement
platform-application/src/main/java/com/performance/platform/application/ports/in/
  ├── ExecuteScenarioUseCase.java
  ├── ScenarioParsingUseCase.java
  ├── GetExecutionStatusUseCase.java
  ├── CancelExecutionUseCase.java
  └── GenerateReportUseCase.java
platform-application/src/main/java/com/performance/platform/application/exception/
  ├── ExecutionException.java
  ├── ReportGenerationException.java
  ├── NoAvailableAgentException.java
  ├── InvalidScenarioException.java
  └── ScenarioParsingException.java   — défini ici pour découpler scenario-dsl du port in

platform-application/src/test/java/com/performance/platform/application/
  └── arch/ApplicationArchitectureTest.java — ArchUnit : 0 import infrastructure/Spring
```

## Interfaces à Implémenter

```java
public interface ExecuteScenarioUseCase { ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException; }
public interface ScenarioParsingUseCase { ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException; }
public interface GetExecutionStatusUseCase {
    ExecutionStatus getStatus(ExecutionId id);
    Optional<ExecutionState> getState(ExecutionId id);
}
public interface CancelExecutionUseCase { void cancel(ExecutionId id); }
public interface GenerateReportUseCase { ReportId generate(ExecutionId id) throws ReportGenerationException; }

public class ExecutionException extends RuntimeException {
    public ExecutionException(String m) { super(m); }
    public ExecutionException(String m, Throwable c) { super(m, c); }
}
public class ReportGenerationException extends RuntimeException { public ReportGenerationException(String m, Throwable c) { super(m, c); } }
public class NoAvailableAgentException extends RuntimeException { public NoAvailableAgentException(String t) { super("No available agent for task: " + t); } }
public class InvalidScenarioException extends RuntimeException { public InvalidScenarioException(String m) { super(m); } }
public class ScenarioParsingException extends RuntimeException {
    private final List<String> errors;
    public ScenarioParsingException(String m, List<String> errors) { super(m); this.errors = List.copyOf(errors); }
    public List<String> getErrors() { return errors; }
}
```

## Règles Spécifiques

- `ScenarioParsingException` est défini ICI (module application) pour que le port `ScenarioParsingUseCase` n'importe pas scenario-dsl. Le module scenario-dsl réutilisera cette classe.
- 0 annotation Spring. ArchUnit interdit `org.springframework..` et `..infrastructure..`.

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur
- [ ] Test ArchUnit passe
- [ ] `.claude/progress.md` mis à jour : ISSUE-012 → DONE
- [ ] `.claude/context/interfaces-registry.md` : use cases → STABLE
