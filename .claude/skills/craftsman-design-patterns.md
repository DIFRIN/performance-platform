# Skill — Craftsman Design Patterns

> Patterns de conception orientés craft à appliquer dans ce projet.
> Lu par le Developer avant d'implémenter, par le Reviewer pour calibrer sa review.
> Ces patterns complètent `precision-patterns.md` (patterns Java spécifiques au projet).

---

## 1. Value Object — Immuabilité et Auto-validation

Tout concept métier sans identité propre devient un Value Object (record Java 25).

```java
// ✅ CORRECT
public record ScenarioId(String value) {
    public ScenarioId {
        Objects.requireNonNull(value, "ScenarioId must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("ScenarioId must not be blank");
        if (!value.matches("[a-z0-9][a-z0-9-]{0,99}"))
            throw new IllegalArgumentException("ScenarioId must be kebab-case, max 100 chars: " + value);
    }
    public static ScenarioId of(String value) { return new ScenarioId(value); }
}

// ✅ Usage — lisible, sans magic string
var id = ScenarioId.of("customer-api-perf");

// ❌ INCORRECT — String brut circulant dans le domaine
public record ScenarioDefinition(String id, ...) {}
// → impossible de distinguer l'id du nom à la lecture
```

---

## 2. Factory Method — Construction Expressive

Préférer des factory methods nommées aux constructeurs directs quand le contexte varie.

```java
// ✅ CORRECT — l'intention est claire à la lecture
public record TaskResult(...) {
    public static TaskResult success(TaskId id, String taskName,
                                     Duration duration, Map<String, Object> outputs) { ... }
    public static TaskResult failed(TaskId id, String taskName,
                                    Duration duration, String error, Throwable cause) { ... }
    public static TaskResult skipped(TaskId id, String taskName, String reason) { ... }
    public static TaskResult timeout(TaskId id, String taskName, Duration elapsed) { ... }
}

// ✅ Usage
return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, e.getMessage(), e);

// ❌ INCORRECT — constructeur avec 8 paramètres, ordre ambigu
return new TaskResult(id, "failed", duration, null, outputs, message, cause, Instant.now());
```

---

## 3. Result Type — Pas d'Exception pour les Échecs Métier

Les échecs prévisibles sont des résultats, pas des exceptions.

```java
// ✅ CORRECT — l'appelant traite explicitement les deux cas
public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
    try {
        var result = doWork(step);
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed(), result);
    } catch (BusinessException e) {
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed(), e.getMessage(), e);
    }
    // Exception technique irrécupérable → laisser remonter (sera catchée par le moteur)
}

// ❌ INCORRECT — mélange exception et résultat
public TaskResult execute(...) throws TaskExecutionException { ... }
// → force l'appelant à gérer deux chemins d'erreur différents
```

---

## 4. Builder pour les Objets Complexes

Quand un record a plus de 5 champs ou des champs optionnels nombreux, utiliser un builder.

```java
// ✅ CORRECT
public record CampaignReport(...) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private ScenarioId scenarioId;
        private List<TaskReportEntry> preparationResults = new ArrayList<>();
        // ...
        public Builder scenarioId(ScenarioId id) { this.scenarioId = id; return this; }
        public Builder addPreparationResult(TaskReportEntry e) { preparationResults.add(e); return this; }
        public CampaignReport build() {
            Objects.requireNonNull(scenarioId, "scenarioId required");
            return new CampaignReport(scenarioId, List.copyOf(preparationResults), ...);
        }
    }
}

// ❌ INCORRECT — constructeur de 12 paramètres
new CampaignReport(id, name, version, tags, meta, env, prep, inj, assertions, verdict, reason, Instant.now());
```

---

## 5. Strategy — Comportement Pluggable sans if/switch

Chaque variante de comportement est une implémentation d'interface, pas une branche.

```java
// ✅ CORRECT — ajout d'un nouveau transport = nouvelle classe, 0 modification
public interface ExecutionTransport {
    void dispatchTask(TaskExecutionRequest request);
    TransportType getType();
}

@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaExecutionTransport implements ExecutionTransport { ... }

@ConditionalOnProperty(name = "transport.type", havingValue = "GRPC")
public class GrpcExecutionTransport implements ExecutionTransport { ... }

// ❌ INCORRECT — OCP violé, chaque ajout modifie le code existant
public void dispatch(TaskExecutionRequest req) {
    if (type == KAFKA) kafkaDispatch(req);
    else if (type == GRPC) grpcDispatch(req);
    else if (type == RABBITMQ) rabbitDispatch(req);
}
```

---

## 6. Observer — Events Internes Découplés

La communication entre composants passe par des events, jamais par injection directe.

```java
// ✅ CORRECT — le moteur ne connaît pas le module Reporting
@Component
public class LocalExecutionEngine {
    private final ApplicationEventPublisher events;

    private void completeTask(TaskResult result, ExecutionId execId) {
        events.publishEvent(new TaskCompleted(execId, result.taskId(),
            result.taskName(), result, result.duration(), Instant.now()));
    }
}

@ApplicationModuleListener
public class ReportingListener {
    @EventListener
    public void on(TaskCompleted event) { /* mise à jour rapport */ }
}

// ❌ INCORRECT — couplage direct entre modules
@Autowired ReportingService reporting;
reporting.onTaskCompleted(result); // viole Spring Modulith
```

---

## 7. Template Method — Squelette d'Algorithme Invariant

Quand la structure d'un algorithme est fixe mais certaines étapes varient.

```java
// ✅ CORRECT — la séquence PREPARATION→INJECTION→ASSERTION est invariante
public abstract class AbstractExecutionEngine implements ExecutionEngine {

    @Override
    public final ExecutionId execute(ScenarioDefinition scenario) {
        var plan = buildPlan(scenario);
        var context = ExecutionContext.initial(ExecutionId.generate(), scenario.id());
        events.publishEvent(new ScenarioStarted(...));

        context = executePhase(plan.preparationSteps(), context);  // varie selon LOCAL/REMOTE
        context = executePhase(plan.injectionSteps(),   context);
        context = executePhase(plan.assertionSteps(),   context);

        generateReport(context);
        return plan.id();
    }

    // Étape variable — implémentée par LocalExecutionEngine ou RemoteExecutionEngine
    protected abstract ExecutionContext executePhase(
        List<ExecutionStep> steps, ExecutionContext context);
}
```

---

## 8. Null Object — Éviter les null checks répétitifs

```java
// ✅ CORRECT — Optional systématique pour les retours potentiellement absents
public interface AgentRegistry {
    Optional<AgentDescriptor> findById(AgentId id);  // jamais null
    List<AgentDescriptor> findByTaskName(String name); // liste vide si aucun
}

// Usage — chaînable, sans null check
registry.findById(agentId)
    .map(AgentDescriptor::httpCallbackUrl)
    .ifPresent(url -> sendCallback(url, event));

// ❌ INCORRECT
AgentDescriptor agent = registry.findById(agentId); // peut retourner null ?
if (agent != null && agent.httpCallbackUrl() != null) { ... }
```

---

## 9. Règles Transversales

### Tell, Don't Ask
```java
// ✅ Dire à l'objet quoi faire
context = context.with("result", taskResult);

// ❌ Demander l'état pour décider à sa place
if (context.getStore().containsKey("result")) { ... }
```

### Composition over Inheritance
```java
// ✅ Composer les comportements
public class RetryingTaskDispatcher implements TaskDispatcher {
    private final TaskDispatcher delegate;
    private final RetryPolicy policy;
    // ...
}

// ❌ Hériter pour ajouter du comportement
public class RetryingExecutionEngine extends LocalExecutionEngine { ... }
```

### Small Methods, Single Responsibility
- Méthode > 20 lignes → extraire une méthode privée nommée
- Classe > 200 lignes → candidat à la décomposition
- Plus d'un `if` imbriqué → early return ou extraction

```java
// ✅ Early return plutôt qu'imbrication
public TaskFilterResult filter(TaskExecutionRequest request) {
    if (!supportedTaskNames.contains(request.step().taskName()))
        return TaskFilterResult.notResponsible(request.id(), agentId);
    if (getState() != AgentState.IDLE)
        return TaskFilterResult.notResponsible(request.id(), agentId);
    return TaskFilterResult.responsible(request.id(), agentId);
}
```
