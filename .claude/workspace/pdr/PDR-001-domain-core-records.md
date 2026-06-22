# PDR-001 — Domain Core Records

**Module Maven** : `platform-domain`
**Package** : `com.performance.platform.domain`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/01-scenario-dsl.md` §6-7, `.claude/knowledge/specs/02-execution-engine.md` §3-5, `.claude/knowledge/specs/03-task-framework.md` §2, `.claude/knowledge/specs/04-agent-runtime.md` §4-7, `.claude/knowledge/specs/06-injection-gatling.md` §5, `.claude/knowledge/specs/07-assertion-framework.md` §3-6
**Dépend de** : rien
**Issues** : ISSUE-001, ISSUE-002, ISSUE-003, ISSUE-004, ISSUE-005, ISSUE-006, ISSUE-007

---

## Responsabilité

Définit le cœur métier immuable de la plateforme : tous les value objects, records,
et enums du domaine, sans aucune dépendance framework. C'est la base sur laquelle
tous les autres modules s'appuient. Aucune logique technique (pas d'I/O, pas de Spring,
pas de JPA, pas de Jackson). Ne contient PAS les events (voir PDR-002) ni les ports
(voir PDR-004).

**Contrainte absolue** : 0 annotation Spring / JPA / Jackson. Que du Java pur.

---

## Interfaces Publiques

> Signatures exactes. `TaskType` n'existe PAS — on utilise `String taskName` partout.

### Identifiants (value objects)

```java
public record ExecutionId(String value) {
    public ExecutionId {
        Objects.requireNonNull(value, "value required");
    }
    public static ExecutionId generate() { return new ExecutionId(UUID.randomUUID().toString()); }
    public static ExecutionId of(String value) { return new ExecutionId(value); }
}

public record ScenarioId(String value) {
    public ScenarioId {
        Objects.requireNonNull(value, "value required");
    }
    public static ScenarioId of(String value) { return new ScenarioId(value); }
}

public record TaskId(String value) {
    public TaskId {
        Objects.requireNonNull(value, "value required");
    }
    public static TaskId of(String value) { return new TaskId(value); }
}

public record AgentId(String value) {
    public AgentId {
        Objects.requireNonNull(value, "value required");
    }
    public static AgentId generate() { return new AgentId(UUID.randomUUID().toString()); }
    public static AgentId of(String value) { return new AgentId(value); }
}

public record MessageId(String value) {
    public MessageId {
        Objects.requireNonNull(value, "value required");
    }
    public static MessageId generate() { return new MessageId(UUID.randomUUID().toString()); }
    public static MessageId of(String value) { return new MessageId(value); }
}

public record EventId(String value) {
    public static EventId generate() { return new EventId(UUID.randomUUID().toString()); }
}

public record SignalId(String value) {
    public static SignalId generate() { return new SignalId(UUID.randomUUID().toString()); }
}

public record ReportId(String value) {
    public static ReportId generate() { return new ReportId(UUID.randomUUID().toString()); }
}
```

### Enums core

```java
public enum Phase { PREPARATION, INJECTION, ASSERTION }

public enum ExecutionMode { LOCAL, DISTRIBUTED }

public enum TaskStatus { SUCCESS, FAILED, SKIPPED, TIMEOUT }

public enum PhaseStatus { PENDING, RUNNING, COMPLETED, FAILED }

public enum AgentState { REGISTERING, IDLE, EXECUTING, DRAINING, OFFLINE }

public enum TaskCompletionPolicy { FIRST_COMPLETE, ALL_COMPLETE }

public enum LoadModelType { RAMP, RAMP_UP_DOWN, CONSTANT, SPIKE, STAIR, SOAK, BURST, CUSTOM }

public enum AssertionOperator {
    LT, LTE, GT, GTE, EQ, NEQ;

    public boolean evaluate(double actual, double expected) {
        return switch (this) {
            case LT  -> actual < expected;
            case LTE -> actual <= expected;
            case GT  -> actual > expected;
            case GTE -> actual >= expected;
            case EQ  -> actual == expected;
            case NEQ -> actual != expected;
        };
    }
}

public enum AssertionStatus { PASSED, FAILED, SKIPPED, ERROR }

public enum Verdict { SUCCESS, WARNING, FAILED }

public enum ExecutionStatus { STARTED, RUNNING, COMPLETED, FAILED, CANCELLED }

public enum TransportType { SOCKET, RABBITMQ, KAFKA, HTTP, IN_MEMORY, CUSTOM }
// NOTE : GRPC supprimé volontairement — non implémenté dans cette plateforme.

public enum PublicationTarget { CONFLUENCE, S3, SHAREPOINT, GIT, NEXUS, CUSTOM }

public enum ReportFormat { HTML, PDF, JSON }
```

### Scenario / Step

```java
public record ScenarioDefinition(
    ScenarioId id,
    String name,
    String version,
    List<String> tags,
    Map<String, String> metadata,
    ExecutionMode executionMode,
    List<StepDefinition> steps,
    Map<String, LoadModel> loadModels
) {
    public ScenarioDefinition {
        Objects.requireNonNull(id, "id required");
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        steps = steps == null ? List.of() : List.copyOf(steps);
        loadModels = loadModels == null ? Map.of() : Map.copyOf(loadModels);
    }
}

public record StepDefinition(
    TaskId id,
    String taskName,                  // nom libre matchant agent.supportedTaskNames
    Phase phase,
    Map<String, Object> parameters,
    List<TaskId> dependsOn,
    List<String> requiredContexts,
    Duration timeout,                 // nullable = default 5min
    RetryPolicy retryPolicy           // nullable = default global
) {
    public StepDefinition {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(phase, "phase required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredContexts = requiredContexts == null ? List.of() : List.copyOf(requiredContexts);
    }
}

public record LoadModel(
    LoadModelType type,
    Map<String, Object> parameters
) {
    public LoadModel {
        Objects.requireNonNull(type, "type required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

public record RetryPolicy(
    int maxAttempts,                  // default 3
    Duration initialDelay,            // default 1s
    double multiplier,                // default 2.0
    Duration maxDelay,                // default 30s
    Set<Class<? extends Exception>> retryableExceptions
) {
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30), Set.of());
    }
}
```

### TaskResult — `String taskName` au lieu de `TaskType`

```java
public record TaskResult(
    TaskId taskId,
    String taskName,                  // remplace TaskType taskType
    TaskStatus status,
    Duration duration,
    Map<String, Object> outputs,
    String errorMessage,              // null si SUCCESS
    Throwable cause,                  // null si SUCCESS
    Instant completedAt
) {
    public TaskResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    public static TaskResult success(TaskId id, String taskName, Duration duration,
                                     Map<String, Object> outputs) {
        return new TaskResult(id, taskName, TaskStatus.SUCCESS, duration,
                outputs, null, null, Instant.now());
    }

    public static TaskResult failed(TaskId id, String taskName, Duration duration,
                                    String message, Throwable cause) {
        return new TaskResult(id, taskName, TaskStatus.FAILED, duration,
                Map.of(), message, cause, Instant.now());
    }

    public static TaskResult skipped(TaskId id, String taskName, String reason) {
        return new TaskResult(id, taskName, TaskStatus.SKIPPED, Duration.ZERO,
                Map.of(), reason, null, Instant.now());
    }

    public boolean isSuccess() { return status == TaskStatus.SUCCESS; }
}
```

### ExecutionContext (immuable) + ExecutionPlan / ExecutionStep / ExecutionState

```java
public record ExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Object> store         // taskId → Map<AgentId(String), TaskResult>
) {
    public ExecutionContext {
        store = store == null ? Map.of() : Map.copyOf(store);
    }

    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) {
        return new ExecutionContext(id, scenarioId, Map.of());
    }

    public ExecutionContext with(String key, Object value) {
        var newStore = new HashMap<>(this.store);
        newStore.put(key, value);
        return new ExecutionContext(executionId, scenarioId, Map.copyOf(newStore));
    }

    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { /* impl */ }
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { /* impl */ }
    public Map<String, TaskResult> getAll(String taskId) { /* impl */ }
}

public record ExecutionStep(
    StepDefinition step,
    List<TaskId> dependencies,
    int dagLevel,                     // 0 = peut démarrer immédiatement
    Set<String> requiredContextKeys
) {}

public record ExecutionPlan(
    ExecutionId id,
    ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps,
    List<ExecutionStep> injectionSteps,
    List<ExecutionStep> assertionSteps,
    ExecutionContext initialContext
) {}

public record ExecutionState(
    ExecutionId id,
    ScenarioId scenarioId,
    ExecutionStatus status,
    Map<Phase, PhaseStatus> phaseStatuses,
    ExecutionContext context,
    Instant startedAt,
    Instant updatedAt
) {}
```

### Agent

```java
public record AgentDescriptor(
    AgentId id,
    String name,
    String host,
    int port,
    String httpCallbackUrl,           // nullable si transport non-HTTP
    Set<String> supportedTaskNames,
    AgentCapabilities capabilities,
    AgentState state,
    Instant registeredAt,
    Instant lastHeartbeatAt,
    Duration registrationTtl
) {
    public AgentDescriptor {
        supportedTaskNames = supportedTaskNames == null ? Set.of() : Set.copyOf(supportedTaskNames);
    }
    public boolean canExecute(String taskName) { return supportedTaskNames.contains(taskName); }
}

public record AgentCapabilities(
    int maxConcurrentTasks,
    String version
) {}

public record AgentHeartbeat(
    AgentId agentId,
    AgentState state,
    int activeTasks,
    Instant sentAt
) {}
```

### Injection / Assertion / Report value objects

```java
public record InjectionResult(
    TaskId taskId,
    String simulationClass,
    Duration duration,
    long totalRequests,
    long successfulRequests,
    long failedRequests,
    double errorRate,                 // pourcentage 0.0 - 100.0
    double throughput,                // requests/second
    long p50Ms,
    long p75Ms,
    long p90Ms,
    long p95Ms,
    long p99Ms,
    long maxMs,
    long minMs,
    double meanMs,
    Path gatlingReportDirectory,
    Map<String, Object> rawStats
) {}

public record AssertionResult(
    TaskId assertionId,
    AssertionStatus status,
    String description,
    Evidence evidence,
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public boolean isPassed() { return status == AssertionStatus.PASSED; }
}

public record Evidence(
    Object actualValue,
    Object expectedValue,
    AssertionOperator operator,
    String unit,
    Map<String, Object> details
) {
    public Evidence {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
```

### PartialExecutionContext (transmis aux agents)

```java
public record PartialExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Map<String, Object>> store   // taskId → (agentId → outputs)
) {
    public PartialExecutionContext {
        store = store == null ? Map.of() : Map.copyOf(store);
    }
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { /* impl */ }
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { /* impl */ }
}
```

---

## Règles de Comportement

- `ExecutionContext` est strictement immuable : `with()` retourne une nouvelle instance,
  jamais de mutation. `store` passé par `Map.copyOf()` dans le constructeur compact.
- `TaskResult` est créé via les factories `success()`, `failed()`, `skipped()` — jamais le constructeur directement par le code métier.
- `getFirst(taskId, type)` : retourne le premier `TaskResult.outputs` castable en `type` parmi les résultats de la task ; `Optional.empty()` si absent.
- `getAll(taskId)` : retourne `Map<AgentId(String), TaskResult>` ou map vide.
- Aucun champ ne doit exposer une collection mutable : toujours `List.copyOf` / `Map.copyOf` / `Set.copyOf`.
- `TaskType` est INTERDIT. Le type de task est porté par `String taskName`.

---

## Dépendances Techniques

```
Ce PDR utilise : rien (cœur du domaine)

Ce PDR est utilisé par : TOUS les autres PDRs.
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] ArchUnit test : 0 import Spring/JPA/Jackson dans `platform-domain`
- [ ] Les records sont dans `.claude/context/interfaces-registry.md` avec statut STABLE
