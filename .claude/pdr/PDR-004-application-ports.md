# PDR-004 — Application Ports & Use Cases

**Module Maven** : `platform-application`
**Package** : `com.performance.platform.application`
**Statut** : WAITING
**Specs de référence** : `.claude/architecture.md` §2-3, `.claude/specifications/02-execution-engine.md` §2, `.claude/specifications/04-agent-runtime.md` §3, `.claude/specifications/08-report-engine.md` §2
**Dépend de** : PDR-001
**Issues** : ISSUE-012, ISSUE-013, ISSUE-014

---

## Responsabilité

Définit les ports (interfaces) entrants (use cases / driving) et sortants (driven : repository,
publisher, registry) selon l'architecture hexagonale. Contient aussi les exceptions
applicatives et les records de configuration applicative (`ExecutionConfig`). Ne contient
AUCUN adapter — uniquement des interfaces. Dépend uniquement de `platform-domain`.

**Contrainte** : ne jamais importer d'adapter infrastructure. Pas de Spring dans les ports
(les implémentations Spring sont dans les modules infra/engine).

---

## Interfaces Publiques

### Ports entrants (driving / use cases)

```java
public interface ExecuteScenarioUseCase {
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
}

public interface ScenarioParsingUseCase {
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
}

public interface GetExecutionStatusUseCase {
    ExecutionStatus getStatus(ExecutionId id);
    Optional<ExecutionState> getState(ExecutionId id);
}

public interface CancelExecutionUseCase {
    void cancel(ExecutionId id);
}

public interface GenerateReportUseCase {
    ReportId generate(ExecutionId id) throws ReportGenerationException;
}
```

### Ports sortants (driven)

```java
public interface ExecutionRepository {
    void save(ExecutionState state);
    Optional<ExecutionState> findById(ExecutionId id);
    void updatePhase(ExecutionId id, Phase phase, PhaseStatus status);
    void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result);
    Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId);
}

public interface AgentRegistryPort {
    void onAgentRegistered(AgentDescriptor descriptor);
    void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
    void onAgentExpired(AgentId agentId);
    void onAgentDeregistered(AgentId agentId);
    List<AgentDescriptor> findByTaskName(String taskName);
    boolean hasAgentFor(String taskName);
    Optional<AgentDescriptor> findById(AgentId agentId);
    List<AgentDescriptor> findAll();
}

public interface ReportPublisherPort {
    void publish(ReportId reportId, ExecutionId executionId);
}
```

### Configuration applicative

```java
public record ExecutionConfig(
    Duration taskAvailabilityTimeout,     // >= 120s en K8s (NOTE R5)
    Duration taskExecutionTimeout,
    Duration workInProgressResetInterval, // <= taskExecutionTimeout / 3 (NOTE R6)
    TaskCompletionPolicy completionPolicy
) {}
```

### Exceptions applicatives

```java
public class ExecutionException extends RuntimeException {
    public ExecutionException(String message) { super(message); }
    public ExecutionException(String message, Throwable cause) { super(message, cause); }
}

public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(String message, Throwable cause) { super(message, cause); }
}

public class NoAvailableAgentException extends RuntimeException {
    public NoAvailableAgentException(String taskName) {
        super("No available agent for task: " + taskName);
    }
}

public class InvalidScenarioException extends RuntimeException {
    public InvalidScenarioException(String message) { super(message); }
}
```

> `ScenarioParsingException` est défini dans PDR-005 (module scenario-dsl) mais référencé ici
> par la signature de `ScenarioParsingUseCase` — l'import vient du module scenario-dsl, donc
> `ScenarioParsingUseCase` est implémenté dans scenario-dsl. La signature ci-dessus dans
> `platform-application` utilise une exception applicative générique ; voir ISSUE-012 règle d'import.

---

## Règles de Comportement

- Les ports sortants sont des interfaces pures : aucune implémentation ici.
- `ExecutionRepository.saveTaskResult()` est appelé N fois pour une task multi-claim (ADR-011).
- `AgentRegistryPort.findByTaskName()` ne fait PAS de sélection — il retourne tous les agents compétents.
- Aucune annotation Spring dans ce module (les `@Service` sont sur les implémentations).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionId, ScenarioId, TaskId, AgentId, ScenarioDefinition,
            ExecutionState, ExecutionStatus, Phase, PhaseStatus, TaskResult,
            AgentDescriptor, AgentHeartbeat, ReportId, TaskCompletionPolicy

Ce PDR est utilisé par :
  PDR-005 (scenario-dsl)     → implémente ScenarioParsingUseCase
  PDR-006 (execution engine) → implémente ExecuteScenarioUseCase, utilise ExecutionRepository
  PDR-009 (agent runtime)    → utilise AgentRegistryPort
  PDR-012 (persistence)      → implémente ExecutionRepository
  PDR-015 (reporting)        → implémente GenerateReportUseCase
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] ArchUnit : 0 import infrastructure, 0 import Spring dans les ports
- [ ] Tous les ports dans `.claude/context/interfaces-registry.md` STABLE
