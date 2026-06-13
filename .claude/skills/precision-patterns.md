# Skill — Patterns de Précision d'Implémentation

> Ce skill liste les erreurs d'implémentation les plus fréquentes sur ce projet
> et les patterns exacts à suivre. Lu par le Developer avant de commencer,
> et par le Reviewer pour calibrer sa review.

---

## Pattern 1 — Record Immuable avec Validation

```java
// ✅ CORRECT — Defensive copy + validation dans le constructeur compact
public record ExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Object> store
) {
    // Constructeur compact : validation + defensive copy
    public ExecutionContext {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        store = store != null ? Map.copyOf(store) : Map.of();
    }

    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) {
        return new ExecutionContext(id, scenarioId, Map.of());
    }

    public ExecutionContext with(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        var newStore = new HashMap<>(this.store);
        newStore.put(key, value);
        return new ExecutionContext(executionId, scenarioId, newStore);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(store.get(key))
            .filter(type::isInstance)
            .map(type::cast);
    }
}

// ❌ INCORRECT — Map mutable exposée
public record ExecutionContext(Map<String, Object> store) {
    public void put(String key, Object value) { store.put(key, value); } // mutation !
}
```

---

## Pattern 2 — TaskExecutor Sans Exception Métier

```java
// ✅ CORRECT — tout échec métier → TaskResult.failed()
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor {

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        var start = Instant.now();
        try {
            var operation = getRequiredParam(step, "operation", String.class);
            var datasource = getRequiredParam(step, "datasource", String.class);
            var rowCount = executeOperation(operation, datasource, step.parameters());
            return TaskResult.success(
                TaskId.of(step.id().value()),
                getSupportedTaskName(),
                Duration.between(start, Instant.now()),
                Map.of("rowsAffected", rowCount)
            );
        } catch (MissingParameterException e) {
            // Erreur de configuration → failed, pas d'exception
            return TaskResult.failed(TaskId.of(step.id().value()), getSupportedTaskName(),
                Duration.between(start, Instant.now()), e.getMessage(), e);
        } catch (SQLException e) {
            return TaskResult.failed(TaskId.of(step.id().value()), getSupportedTaskName(),
                Duration.between(start, Instant.now()),
                "Database error: " + e.getMessage(), e);
        }
        // NE PAS laisser d'exception non catchée sortir de execute()
    }

    private <T> T getRequiredParam(StepDefinition step, String key, Class<T> type) {
        var value = def.parameters().get(key);
        if (value == null) throw new MissingParameterException(key, step.id());
        if (!type.isInstance(value)) throw new MissingParameterException(key + " must be " + type.getSimpleName(), step.id());
        return type.cast(value);
    }
}

// ❌ INCORRECT — exception qui remonte
public TaskResult execute(...) {
    var conn = dataSource.getConnection(); // peut lancer SQLException non catchée !
    // ...
}
```

---

## Pattern 3 — Auto-Enregistrement TaskExecutor

```java
// ✅ CORRECT — Spring collecte tous les TaskExecutor automatiquement
@Component
public class DefaultTaskExecutorRegistry implements TaskExecutorRegistry {

    private final Map<String, TaskExecutor> registry;

    // Spring injecte tous les beans TaskExecutor annotés @Preparation/@Injection/@Assertion
    // Clé = getSupportedTaskName() — String libre, pas d'enum
    public DefaultTaskExecutorRegistry(List<TaskExecutor> executors) {
        this.registry = executors.stream()
            .collect(Collectors.toUnmodifiableMap(
                TaskExecutor::getSupportedTaskName,
                Function.identity(),
                (a, b) -> { throw new DuplicateTaskExecutorException(a.getSupportedTaskName()); }
            ));
    }

    @Override
    public TaskExecutor getFor(String taskName) {
        return Optional.ofNullable(registry.get(taskName))
            .orElseThrow(() -> new UnsupportedTaskTypeException(taskName));
    }
}

// ❌ INCORRECT — registre manuel
// ❌ registration manuelle — ne plus utiliser
// registry.register("database", new DatabaseTaskExecutor(...));
// → ajouter un type = modifier ce code
// → Utiliser @Preparation/@Injection/@Assertion + Spring auto-discovery
```

---

## Pattern 4 — Event Publishing Inter-Modules

```java
// ✅ CORRECT — découplé via Spring Events
@Component
public class LocalExecutionEngine implements ExecutionEngine {

    private final ApplicationEventPublisher events;

    private void onTaskCompleted(TaskResult result, ExecutionId execId) {
        events.publishEvent(new TaskCompleted(execId, result.taskId(),
            result.taskType(), result, result.duration(), Instant.now()));
    }
}

// ✅ CORRECT — listener dans le module Reporting
@ApplicationModuleListener  // Spring Modulith — async par défaut
public class ReportingEventListener {
    @EventListener
    public void on(TaskCompleted event) {
        // Mise à jour des métriques du rapport en cours
    }
}

// ❌ INCORRECT — injection directe entre modules
@Autowired
private ReportingService reportingService; // couplage direct !
reportingService.recordTaskResult(result);
```

---

## Pattern 5 — DAG Execution avec Virtual Threads

```java
// ✅ CORRECT — parallel execution par niveau de DAG
private ExecutionContext executePhase(
    List<ExecutionStep> steps,
    ExecutionContext context
) {
    // Grouper par dagLevel
    var byLevel = steps.stream()
        .collect(Collectors.groupingBy(ExecutionStep::dagLevel));

    var currentContext = context;

    // Exécuter niveau par niveau (séquentiel entre niveaux, parallèle dans chaque niveau)
    for (var level : byLevel.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        currentContext = executeLevel(level.getValue(), currentContext);
    }
    return currentContext;
}

private ExecutionContext executeLevel(
    List<ExecutionStep> steps,
    ExecutionContext context
) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures = steps.stream()
            .map(step -> executor.submit(() -> executeStep(step, context)))
            .toList();

        var results = futures.stream()
            .map(this::getResult)   // join sur chaque future
            .toList();

        // Fold des résultats dans le contexte (immuable)
        return results.stream()
            .reduce(context,
                (ctx, result) -> ctx.with(conventionKey(result), result),
                (a, b) -> b); // pas de merge parallèle — exécution séquentielle du fold
    }
}

// ❌ INCORRECT — Thread.sleep() pour attendre
Thread.sleep(1000); // jamais dans le code production
```

---

## Pattern 6 — Clés ExecutionContext par Convention

```java
// ✅ CORRECT — la clé = step.id() (String). Pas de méthode contextKey() nécessaire.

// Dans l'executor — retourner les outputs dans TaskResult :
return TaskResult.success(
    TaskId.of(step.id().value()),
    getSupportedTaskName(),
    elapsed,
    Map.of("result", myOutput)
);
// L'orchestrateur mergera : context["step-id"]["agent-id"] = taskResult

// Dans une assertion — lire depuis le contexte partiel reçu :
var result = context.getFirst("inject", InjectionResult.class);      // premier agent
var allResults = context.getAll("inject");                            // tous les agents

// ❌ INCORRECT — ancien schéma de clés préfixées (supprimé)
// context.get("gatling.customer-api-load.result", InjectionResult.class)
// context.get("database.purge-db.outputs", Map.class)
// → Ces clés n'existent plus dans le nouveau modèle
```

---

## Pattern 7 — Test Unitaire d'un Record

```java
// ✅ CORRECT — tester l'immuabilité ET le comportement
class ExecutionContextTest {

    @Test
    void initialContextShouldBeEmpty() {
        var ctx = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s1"));
        assertThat(ctx.store()).isEmpty();
    }

    @Test
    void withShouldReturnNewContextWithoutMutatingOriginal() {
        var original = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s1"));
        var updated = original.with("key", "value");

        assertThat(original.store()).doesNotContainKey("key");  // original inchangé
        assertThat(updated.store()).containsEntry("key", "value");
        assertThat(updated).isNotSameAs(original);
    }

    @Test
    void getShouldReturnTypedValueWhenPresent() {
        var ctx = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s1"))
            .with("count", 42L);

        assertThat(ctx.get("count", Long.class)).contains(42L);
        assertThat(ctx.get("count", String.class)).isEmpty(); // wrong type → empty
        assertThat(ctx.get("missing", Long.class)).isEmpty(); // missing → empty
    }

    @Test
    void constructorShouldRejectNullExecutionId() {
        assertThrows(NullPointerException.class,
            () -> new ExecutionContext(null, ScenarioId.of("s1"), Map.of()));
    }
}
```
