# Spec 02 — Execution Engine

**Module** : `platform-execution-engine`  
**Dépend de** : `platform-domain`, `platform-application`, `platform-scenario-dsl`

---

## 1. Objectif

Transformer un `ScenarioDefinition` en `ExecutionPlan`, puis orchestrer
l'exécution des trois phases en séquence obligatoire :
`PREPARATION → INJECTION → ASSERTION`.

Deux implémentations : `LocalExecutionEngine` et `RemoteExecutionEngine`.

---

## 2. Interfaces Publiques

```java
// Port entrant principal
public interface ExecutionEngine {
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
    ExecutionStatus getStatus(ExecutionId id);
    void cancel(ExecutionId id);
}

// Port entrant : construction du plan
public interface ExecutionPlanBuilder {
    ExecutionPlan build(ScenarioDefinition scenario);
}

// Port sortant : persistance de l'état
public interface ExecutionRepository {
    void save(ExecutionState state);
    Optional<ExecutionState> findById(ExecutionId id);
    void updatePhase(ExecutionId id, Phase phase, PhaseStatus status);
    /**
     * Persiste le résultat d'une task pour un agent donné.
     * Appelé N fois pour la même task si N agents ont claimé (multi-claim).
     */
    void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result);
    Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId);
}
```

---

## 3. Modèle ExecutionPlan

```java
public record ExecutionPlan(
    ExecutionId id,
    ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps,   // triés par DAG
    List<ExecutionStep> injectionSteps,     // triés par DAG
    List<ExecutionStep> assertionSteps,     // triés par DAG
    ExecutionContext initialContext
) {}

public record ExecutionStep(
    StepDefinition step,                    // contient taskName, phase, requiredContexts
    List<TaskId> dependencies,              // déjà résolues
    int dagLevel,                           // 0 = peut démarrer immédiatement
    Set<String> requiredContextKeys         // résolu depuis step.requiredContexts()
) {}
```

---

## 4. ExecutionContext

```java
// IMMUABLE - utiliser withXxx() pour créer une nouvelle version
public record ExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Object> store             // résultats des tâches précédentes
) {
    // Factory
    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) { ... }

    // Ajout immuable d'une entrée
    public ExecutionContext with(String key, Object value) {
        var newStore = new HashMap<>(this.store);
        newStore.put(key, value);
        return new ExecutionContext(executionId, scenarioId, Map.copyOf(newStore));
    }

    // Structure du store après évolution :
    // Clé = taskId (String)
    // Valeur = Map<AgentId, TaskResult>
    //
    // Exemple :
    //   "login"  → { "agent-auth-01": TaskResult(outputs={token: "eyJ..."}) }
    //   "inject" → { "agent-perf-01": TaskResult(outputs=InjectionResult),
    //                "agent-perf-02": TaskResult(outputs=InjectionResult) }
    //
    // Pour les assertions : utiliser getFirst() pour obtenir le premier résultat disponible.
    // Pour les scénarios multi-agents : itérer sur tous les résultats de la task.
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { ... }
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { ... }
    public Map<String, TaskResult> getAll(String taskId) { ... }
}
```

---

## 5. Algorithme d'Exécution des Phases

### Règle de séquencement (OBLIGATOIRE)
```
Phase PREPARATION termine entièrement (SUCCESS ou FAILED)
  → si FAILED et stopOnFailure: true → arrêt campagne
  → sinon → Phase INJECTION
Phase INJECTION termine
  → Phase ASSERTION (toujours, même si INJECTION failed)
Phase ASSERTION termine
  → Génération du rapport
```

### Algorithme DAG pour chaque phase
```
1. Grouper les steps par dagLevel
2. Pour chaque niveau, en parallèle (Virtual Threads) :
   a. Attendre que tous les prérequis (dagLevel - 1) soient terminés
   b. Si un prérequis a FAILED : marquer le step SKIPPED (sauf si dépendance optionnelle)
   c. Exécuter le TaskExecutor
   d. Stocker le résultat dans ExecutionContext
3. Passer au niveau suivant
```

### Retry
```java
// Config par tâche ou globale
public record RetryPolicy(
    int maxAttempts,          // default: 3
    Duration initialDelay,    // default: 1s
    double multiplier,        // default: 2.0 (backoff exponentiel)
    Duration maxDelay,        // default: 30s
    Set<Class<? extends Exception>> retryableExceptions
) {}
```

---

## 6. LocalExecutionEngine

Exécute tout dans le JVM courant avec Virtual Threads.

```java
@Service
// runtime.mode is set via: RUNTIME_MODE env var (priority) or application.yaml (default)
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecutionEngine {

    // Exécution en parallèle des tâches indépendantes via Virtual Threads
    // Executor : Executors.newVirtualThreadPerTaskExecutor()

    // Séquence :
    // 1. build ExecutionPlan
    // 2. persist ExecutionState (STARTED)
    // 3. executePhase(PREPARATION)
    // 4. executePhase(INJECTION)
    // 5. executePhase(ASSERTION)
    // 6. persist ExecutionState (COMPLETED | FAILED)
    // 7. publish ScenarioFinished event
}
```

---

## 7. RemoteExecutionEngine

Distribue les tâches vers les agents via `ExecutionTransport`.

```java
@Service
// runtime.mode is set via: RUNTIME_MODE env var (priority) or application.yaml (default)
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecutionEngine {

    // Séquence :
    // 1. build ExecutionPlan
    // 2. Vérifier présence d'agents compétents via AgentAvailabilityChecker
    //    NOTE R5 : attendre jusqu'à taskAvailabilityTimeout (voir ExecutionConfig)
    //    Prévoir taskAvailabilityTimeout > temps de démarrage d'un agent (min 120s en K8s)
    // 3. pour chaque step (selon DAG + dagLevel) :
    //    a. Construire PartialExecutionContext depuis step.requiredContextKeys
    //    b. Créer TaskExecutionRequest (sans targetAgentId — broadcast)
    //    c. transport.dispatchTask(request)
    //    d. TaskCorrelationTracker.trackDispatched(messageId, taskId, executionId)
    // 4. Collecter les events (subscribe) :
    //    - TaskClaimedByAgent : enregistrer le claim (multi-agent accepté)
    //    - TaskWorkInProgress : reset du timeout de la task
    //      NOTE R6 : workInProgressIntervalSeconds doit satisfaire
    //      workInProgressInterval <= taskExecutionTimeout / 3
    //    - TaskCompleted : stocker dans context["taskId"]["agentId"]
    //    - TaskFailed : marquer comme failed, retry si configuré
    // 5. Selon completionPolicy (FIRST_COMPLETE | ALL_COMPLETE) :
    //    attendre 1 ou N completions avant de passer au step suivant
    // 6. Continuer selon le même algorithme DAG que LOCAL
}
```

---

## 8. Events Publiés

```java
// Publier via ApplicationEventPublisher (Spring Events)

// Cycle de vie du scénario (inchangé)
ScenarioStarted(executionId, scenarioId, timestamp)
ScenarioFinished(executionId, scenarioId, verdict, duration, timestamp)
ScenarioCancelled(executionId, scenarioId, reason, timestamp)
PhaseStarted(executionId, phase, timestamp)
PhaseCompleted(executionId, phase, status, timestamp)

// Cycle de vie d'une task (enrichi)
TaskDispatched(executionId, taskId, taskName, messageId, timestamp)
TaskClaimedByAgent(executionId, taskId, agentId, messageId, timestamp)

// NOTE R6 : TaskWorkInProgress est publié par l'agent à intervalles réguliers.
// L'orchestrateur reset son timer de timeout à chaque réception.
// Règle : agent.workInProgressIntervalSeconds <= taskExecutionTimeout.toSeconds() / 3
TaskWorkInProgress(executionId, taskId, agentId, progressPercent, statusMessage, timestamp)

TaskCompleted(executionId, taskId, agentId, result, duration, timestamp)
TaskFailed(executionId, taskId, agentId, error, attempt, timestamp)
TaskRetried(executionId, taskId, attempt, nextAttemptAt, timestamp)

// Restart orchestrateur (nouveau)
ScenarioRestartSignal(executionId, reason, timestamp)  // broadcast vers tous les agents
```

---

## 9. ExecutionConfig

```java
public record ExecutionConfig(
    /**
     * Durée maximale d'attente d'un agent compétent avant de FAIL la task.
     * NOTE R5 : doit être supérieur au temps de démarrage d'un agent dans
     * l'environnement cible. Valeur recommandée en K8s : >= 120s.
     * En dev local, 30s est généralement suffisant.
     */
    Duration taskAvailabilityTimeout,

    /**
     * Durée maximale d'exécution d'une task avant TIMEOUT.
     */
    Duration taskExecutionTimeout,

    /**
     * Délai sans TaskWorkInProgress avant de considérer l'agent en timeout.
     * NOTE R6 : doit satisfaire workInProgressResetInterval <= taskExecutionTimeout / 3
     * pour éviter les faux positifs sous charge ou latence réseau.
     */
    Duration workInProgressResetInterval,

    /**
     * Politique de complétion pour les tasks multi-agents.
     * FIRST_COMPLETE : avancer dès le premier résultat reçu.
     * ALL_COMPLETE : attendre tous les agents ayant claimé.
     */
    TaskCompletionPolicy completionPolicy
) {}
```

---

## 10. Gestion des Erreurs


| Scénario | Comportement |
|---|---|
| Task FAILED, retry épuisé | Phase marquée FAILED, continuer selon config `continueOnFailure` |
| Task TIMEOUT | Traité comme FAILED, retry si configuré |
| Cycle dans DAG | `InvalidScenarioException` au build du plan (avant exécution) |
| Agent perdu (TTL expiré) | ScenarioRestartSignal broadcast → agents cleanup → scénario FAILED |
| Aucun agent pour la task | Attendre taskAvailabilityTimeout → NoAvailableAgentException → scénario FAILED |
| Orchestrator restart | Reprendre depuis dernier checkpoint persisté |
