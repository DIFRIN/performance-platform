# PDR-010 — Task Executors (Infrastructure)

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.executor`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/03-task-framework.md` §1-6, ADR-007
**Dépend de** : PDR-001, PDR-003, PDR-004
**Issues** : ISSUE-039, ISSUE-040, ISSUE-041, ISSUE-042, ISSUE-043, ISSUE-044, ISSUE-045

---

## Responsabilité

Implémente les `TaskExecutor` internes de préparation (database, kafka-consumer,
kafka-producer, mock-server, shell, docker, filesystem) et le `TaskExecutorRegistry`
(résolution par `String taskName`). Chaque executor porte une annotation
`@Preparation/@Injection/@Assertion` et stocke ses outputs dans `TaskResult.outputs`.

**Séparation stricte** : tout dans `com.performance.platform.infrastructure.executor/`.
Ne déborde PAS sur `.plugin` (PDR-011), `.persistence` (PDR-012), `.publisher` (PDR-016).

---

## Interfaces Publiques

```java
public interface TaskExecutorRegistry {
    void register(TaskExecutor executor);
    TaskExecutor getFor(String taskName) throws UnsupportedTaskTypeException;
    Set<String> getSupportedTaskNames();
}

@Component
public class DefaultTaskExecutorRegistry implements TaskExecutorRegistry {
    public DefaultTaskExecutorRegistry(List<TaskExecutor> executors) {
        executors.forEach(this::register);   // clé = getSupportedTaskName() (String)
    }
}

public class UnsupportedTaskTypeException extends RuntimeException {
    public UnsupportedTaskTypeException(String taskName) {
        super("No TaskExecutor registered for taskName: " + taskName);
    }
}
```

### Executors internes (annotations + signatures)

```java
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "database"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}

@Preparation(name = "kafka-consumer", description = "Kafka consume/monitor/count")
public class KafkaConsumerTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "kafka-consumer"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}

@Preparation(name = "kafka-producer", description = "Kafka preload/produce")
public class KafkaProducerTaskExecutor implements TaskExecutor { /* ... */ }

@Preparation(name = "mock-server", description = "WireMock embedded/external")
public class MockServerTaskExecutor implements TaskExecutor { /* ... */ }

@Preparation(name = "shell", description = "Shell command execution")
public class ShellTaskExecutor implements TaskExecutor { /* ... */ }

@Preparation(name = "docker", description = "Docker start/stop/pull")
public class DockerTaskExecutor implements TaskExecutor { /* ... */ }

@Preparation(name = "filesystem", description = "FS create/delete/upload/cleanup")
public class FilesystemTaskExecutor implements TaskExecutor { /* ... */ }
```

---

## Règles de Comportement

- Un executor NE LÈVE PAS d'exception pour un échec métier → `TaskResult.failed(...)`.
- Clé de registre = `executor.getSupportedTaskName()` (String) — JAMAIS de switch/if sur un type.
- Outputs stockés dans `TaskResult.outputs` (ex DATABASE : `{rowsAffected, duration}` ; SHELL : `{exitCode, stdout, stderr}` ; MOCK_SERVER : `{port, url}`).
- Paramètres lus depuis `step.parameters()` (Map<String,Object>).
- `TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs)` — `String taskName`, pas `TaskType`.
- Datasources référencées par nom depuis la config globale (`application.yaml`).
- Tout I/O bloquant → Virtual Threads. Respecter timeouts de `step.timeout()`.
- Cleanup stateful exposé pour le restart signal (ferme connexions/process/containers).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionContext, StepDefinition, TaskResult, TaskId
  PDR-003 → TaskExecutor, @Preparation
  PDR-004 → (datasources via config)

Ce PDR est utilisé par :
  PDR-009 (agent runtime) → invoque TaskExecutorRegistry.getFor(taskName).execute()
  PDR-006 (engine LOCAL)  → idem
  PDR-011 (plugin system) → fusionne executors internes + externes
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] ArchUnit : ce code reste dans le package `.executor` uniquement
- [ ] Tests Testcontainers pour DatabaseTaskExecutor (PostgreSQL), KafkaConsumer
- [ ] `TaskExecutorRegistry` dans `.claude/context/interfaces-registry.md` STABLE
