# Spec 01 — Scenario DSL

**Module** : `platform-scenario-dsl`  
**Dépend de** : `platform-domain`

---

## 1. Objectif

Définir le format YAML des scénarios et des load models, les règles de validation,
et les interfaces de parsing. La sortie de ce module est un `ScenarioDefinition`
immuable utilisable par le moteur d'exécution.

---

## 2. Structure YAML d'un Scénario

```yaml
scenario:
  id: customer-api-perf                # REQUIRED - unique
  name: Customer API Campaign          # REQUIRED
  version: 1.0.0                       # REQUIRED - semver
  tags:
    - performance
    - regression
  metadata:
    owner: team-a
    jira: PERF-123
    description: "Test de performance de l'API client"

  execution:
    mode: DISTRIBUTED                  # LOCAL | DISTRIBUTED

  # NOTE R5 : taskAvailabilityTimeoutSeconds — durée d'attente d'un agent compétent.
  # Doit être supérieur au temps de démarrage d'un agent (min 120s en K8s).
  taskAvailabilityTimeoutSeconds: 120

  steps:
    - id: purge-db
      task: database                   # matché avec agent.supportedTasks
      phase: PREPARATION
      requiredContexts: []             # aucun contexte requis
      dependsOn: []
      parameters:
        operation: PURGE
        datasource: customer-db
      timeout: 30s

    - id: kafka-monitor
      task: kafka-consumer
      phase: PREPARATION
      requiredContexts: []
      dependsOn: []
      parameters:
        topic: customer-events
        groupId: perf-monitor

    - id: start-mock
      task: mock-server
      phase: PREPARATION
      requiredContexts: []
      dependsOn: []
      parameters:
        deployment: EMBEDDED
        port: 8090
        mappingsPath: wiremock/mappings

    - id: customer-api-load
      task: gatling
      phase: INJECTION
      requiredContexts:
        - purge-db                     # l'agent recevra context["purge-db"]
        - start-mock                   # et context["start-mock"]
      dependsOn:
        - purge-db
        - start-mock
      parameters:
        simulation: com.example.CustomerApiSimulation
        loadModel: api-load
      timeout: 20m

    - id: error-rate-check
      task: gatling-metric
      phase: ASSERTION
      requiredContexts:
        - customer-api-load            # l'agent recevra context["customer-api-load"]
      dependsOn:
        - customer-api-load
      parameters:
        metric: errorRate
        operator: LT
        value: 1.0

    - id: p95-check
      task: gatling-metric
      phase: ASSERTION
      requiredContexts:
        - customer-api-load
      dependsOn:
        - customer-api-load
      parameters:
        metric: p95
        operator: LT
        value: 500

    - id: db-count-check
      task: database-assertion
      phase: ASSERTION
      requiredContexts: []
      dependsOn:
        - customer-api-load
      parameters:
        datasource: customer-db
        query: "SELECT COUNT(*) FROM orders WHERE created_at > NOW() - INTERVAL '1 hour'"
        operator: GT
        value: 1000

loadModels:
  api-load:
    type: RAMP
    stages:
      - duration: 2m
        usersPerSecond: 10
      - duration: 5m
        usersPerSecond: 100
      - duration: 5m
        usersPerSecond: 300
      - duration: 2m
        usersPerSecond: 0
```

---

## 3. Load Model Types

### RAMP
```yaml
type: RAMP
stages:
  - duration: 2m
    usersPerSecond: 10
  - duration: 5m
    usersPerSecond: 100
```

### CONSTANT
```yaml
type: CONSTANT
usersPerSecond: 100
duration: 10m
```

### SPIKE
```yaml
type: SPIKE
baseUsersPerSecond: 50
spikeUsersPerSecond: 500
spikeDuration: 30s
baseDuration: 5m
```

### STAIR
```yaml
type: STAIR
initialUsersPerSecond: 10
incrementPerStep: 20
stepDuration: 2m
steps: 5
```

### SOAK
```yaml
type: SOAK
usersPerSecond: 50
duration: 2h
rampUpDuration: 5m
```

### BURST
```yaml
type: BURST
burstUsersPerSecond: 1000
burstDuration: 10s
burstCount: 3
intervalBetweenBursts: 2m
```

### RAMP_UP_DOWN
```yaml
type: RAMP_UP_DOWN
usersPerSecond: 200
rampUpDuration: 5m
holdDuration: 10m
rampDownDuration: 5m
```

### CUSTOM
```yaml
type: CUSTOM
points:
  - time: 0s
    usersPerSecond: 0
  - time: 60s
    usersPerSecond: 100
  - time: 300s
    usersPerSecond: 50
```

---

## 4. Interfaces de Parsing

```java
// Port entrant
public interface ScenarioParser {
    ScenarioDefinition parse(InputStream yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parseFile(Path scenarioFile) throws ScenarioParsingException;
}

// Port entrant
public interface ScenarioValidator {
    ValidationResult validate(ScenarioDefinition scenario);
}

// Registre des load models réutilisables
public interface LoadModelRegistry {
    void register(String name, LoadModel model);
    LoadModel get(String name) throws LoadModelNotFoundException;
    Map<String, LoadModel> getAll();
}
```

---

## 5. Règles de Validation

### Erreurs (bloquantes)
- `scenario.id` : obligatoire, alphanumérique + tirets, max 100 chars
- `scenario.version` : obligatoire, format semver strict
- Chaque `step.id` : unique au sein du scénario
- `dependsOn` : référence des IDs existants dans le même scénario
- `dependsOn` : pas de cycle (DAG valide)
- `requiredContexts` : toutes les clés référencées doivent être l'id d'un step
  antérieur dans l'ordre topologique du DAG
- `loadModel` dans un step INJECTION de type `gatling` : référence résolue
- `phase` : obligatoire sur chaque step (PREPARATION | INJECTION | ASSERTION)
- `task` : obligatoire — le `taskName` n'est pas validé contre un registre au parse time
  (les agents ne sont pas connus à ce stade)

### Avertissements (non bloquants)
- Pas de `metadata.owner` : warning
- `timeout` non défini sur step INJECTION : warning
- Scénario sans step de phase ASSERTION : warning
- `requiredContexts` vide sur un step ASSERTION : warning (les assertions lisent généralement un contexte)

---

## 6. Modèle de Données (Domain)

```java
// Tous les records sont immuables
public record ScenarioDefinition(
    ScenarioId id,
    String name,
    String version,
    List<String> tags,
    Map<String, String> metadata,
    ExecutionMode executionMode,
    List<StepDefinition> steps,           // liste unifiée — remplace preparation/injections/assertions
    Map<String, LoadModel> loadModels
) {}

/**
 * StepDefinition remplace TaskDefinition.
 * La phase est explicite (PREPARATION | INJECTION | ASSERTION).
 * requiredContexts déclare les entrées de contexte nécessaires à l'exécution.
 */
public record StepDefinition(
    TaskId id,                            // identifiant unique dans le scénario
    String taskName,                      // nom libre matchant agent.supportedTasks
    Phase phase,                          // PREPARATION | INJECTION | ASSERTION
    Map<String, Object> parameters,       // paramètres spécifiques à la task
    List<TaskId> dependsOn,               // dépendances DAG
    List<String> requiredContexts,        // clés de l'ExecutionContext à transmettre à l'agent
    Duration timeout,                     // nullable = default 5min
    RetryPolicy retryPolicy               // nullable = default global
) {}

public record LoadModel(
    LoadModelType type,
    Map<String, Object> parameters
) {}
```

---

## 7. Exceptions

```java
public class ScenarioParsingException extends RuntimeException {
    private final List<String> errors;   // liste des erreurs de parsing YAML
}

public class ScenarioValidationException extends RuntimeException {
    private final ValidationResult result; // détail des erreurs de validation
}

public record ValidationResult(
    boolean valid,
    List<ValidationError> errors,
    List<ValidationWarning> warnings
) {}

public record ValidationError(String field, String message, String path) {}
public record ValidationWarning(String field, String message) {}
```
