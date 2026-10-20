# Spec 04 — Agent Runtime

**Module** : `platform-agent-runtime`
**Dépend de** : `platform-domain`, `platform-application`, `platform-transport`,
`platform-plugin-api`

---

## 1. Objectif

Un agent est une instance JVM qui :
1. se connecte au transport ;
2. en DISTRIBUTED, s'enregistre et envoie un heartbeat ;
3. reçoit les `TaskExecutionRequest` en broadcast ;
4. filtre localement selon ses `supportedTaskNames` ;
5. exécute les tâches et publie claim, progression et résultat ;
6. libère ses ressources stateful sur signal de restart ;
7. draine ses tâches à l'arrêt.

Il existe **un seul runtime** (`TaskAgentRuntime`) pour LOCAL et DISTRIBUTED (ADR-037).
La différence est portée par `AgentRegistrationPort` :
- `registrationPort == null` → LOCAL : pas de REGISTERING, pas de heartbeat,
  pas de deregister ;
- `registrationPort != null` → DISTRIBUTED : enregistrement, heartbeat, deregister.

> **Aucune surface d'accès en mode AGENT (ADR-019).** La JVM démarre avec
> `WebApplicationType.NONE` : ni API REST, ni IHM, ni CLI, pas de Tomcat. L'agent est un
> pur worker piloté par le transport. Le récepteur HTTP de callbacks
> (`HttpEventCallbackController`, actif seulement si `transport.type=HTTP`) est un canal
> interne du transport, pas une surface d'accès.

---

## 2. Cycle de vie

```mermaid
stateDiagram-v2
    [*] --> OFFLINE
    OFFLINE --> REGISTERING: start() · DISTRIBUTED
    OFFLINE --> IDLE: start() · LOCAL
    REGISTERING --> IDLE: registered
    REGISTERING --> OFFLINE: registration failed
    IDLE --> EXECUTING: task claimed
    EXECUTING --> IDLE: activeTasks == 0
    IDLE --> DRAINING: stop()
    EXECUTING --> DRAINING: stop()
    DRAINING --> OFFLINE: drained
    EXECUTING --> IDLE: ScenarioRestartSignal
```

Règles :
- `start()` est idempotent : deuxième appel ignoré avec WARN.
- `stop()` est idempotent ; il passe en DRAINING, attend les tâches actives
  (timeout = 2 × `taskExecutionTimeout`), annule le reste, arrête le heartbeat,
  se désenregistre, se déconnecte puis passe OFFLINE.
- Un agent OFFLINE ou DRAINING ignore les nouvelles tâches.

---

## 3. Interfaces

```java
public interface AgentRuntime {
    void start();
    void stop();
    AgentState getState();
    AgentDescriptor getDescriptor();
    boolean canExecute(String taskName);
    void onScenarioRestart(ScenarioRestartSignal signal);
    void onLifecycleSignal(ExecutionLifecycleSignal signal);
}

public interface AgentRegistrationPort {
    void register(AgentDescriptor descriptor) throws RegistrationException;
    void deregister(AgentId agentId);
    void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
}

// Côté orchestrateur
public interface AgentRegistry extends AgentRegistryPort { }   // marqueur, impl. InMemoryAgentRegistry

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
```

`TaskAgentRuntime` collabore avec :
- `TaskChannel`, `SignalChannel`, `EventChannel`, `ConnectionLifecycle` (ports étroits,
  ADR-028) ;
- `TaskSpecializationFilter` ;
- `TaskExecutionPipeline` ;
- `ScenarioRestartHandler` ;
- `DefaultLifecycleSignalHandler` ;
- optionnellement `HeartbeatScheduler` + `AgentRegistrationPort`.

---

## 4. AgentDescriptor

```java
public record AgentDescriptor(
    AgentId id,
    String name,
    String host,
    int port,
    String httpCallbackUrl,          // nullable si transport != HTTP
    Set<String> supportedTaskNames,  // ADR-015 : depuis agent.supported-tasks (DISTRIBUTED)
    AgentCapabilities capabilities,
    AgentState state,
    Instant registeredAt,
    Instant lastHeartbeatAt,
    Duration registrationTtl        // >= 3 × heartbeat interval
) {}

public record AgentCapabilities(int maxConcurrentTasks, String version) {}
public record AgentHeartbeat(AgentId agentId, AgentState state, int activeTasks, Instant sentAt) {}
```

En LOCAL, le descripteur est construit avec **tous** les `TaskExecutor` du contexte et
l'identité `local-agent`. En DISTRIBUTED, `supportedTaskNames` est dérivé
exclusivement de `agent.supported-tasks` (surchargeable par `AGENT_SUPPORTED_TASKS`).

---

## 5. Spécialisation (`TaskSpecializationFilter`)

```java
public interface TaskSpecializationFilter {
    TaskFilterResult filter(TaskExecutionRequest request);
    boolean canExecute(String taskName);
}

public sealed interface TaskFilterResult
        permits TaskFilterResult.Responsible, TaskFilterResult.NotResponsible {
    record Responsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
    record NotResponsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
}
```

`DefaultTaskSpecializationFilter` :
- `Responsible` si `request.step().taskName()` ∈ `supportedTaskNames` ;
- `NotResponsible` sinon (log DEBUG, aucun appel réseau) ;
- décision purement locale, aucun agentTags, aucune coordination (ADR-015).

Le runtime vérifie en plus l'idempotence via `MessageId` : un message déjà traité est
ignoré. Le set `processedMessageIds` est purgé au-delà de 10 000 entrées.

---

## 6. Pipeline d'exécution

`TaskExecutionPipeline.execute(request)` :
1. planifie la publication périodique de `TaskWorkInProgress`
   (intervalle = `taskExecutionTimeout / 3`, minimum 1 s) ;
2. résout le `TaskExecutor` par `step.taskName()` ; absent → `TaskResult.failed`
   publié immédiatement ;
3. convertit le `PartialExecutionContext` en `ExecutionContext` local (les outputs
   nommés sont exposés tels quels ; les anciens payloads bruts restent supportés) ;
4. exécute `executor.execute(context, step)` sur un Virtual Thread ;
5. publie `TASK_COMPLETED` (succès) ou `TASK_FAILED` (échec) avec payload :
   `taskName`, `taskId`, `status`, `durationMs`, `outputs` (succès), `error` (échec) ;
6. en `finally` : arrête la progression, retire la tâche active, décrémente le compteur
   et repasse `EXECUTING → IDLE` si plus aucune tâche active.

Le claim `TASK_CLAIMED` est publié par le runtime **avant** la soumission du pipeline.

---

## 7. Enregistrement, heartbeat, TTL (DISTRIBUTED)

Côté agent (`TransportAgentRegistration`, `LifecycleChannel`) :
- `register` → `AgentLifecycleEvent(type=AGENT_REGISTERED, payload=AgentDescriptor)`
  (codec typé `AgentLifecyclePayloadCodec`) ;
- `sendHeartbeat` → `AGENT_HEARTBEAT` ;
- `deregister` → `AGENT_DEREGISTERED`.

`HeartbeatScheduler` :
- premier heartbeat immédiat puis toutes les `intervalSeconds` (10 s dans le bean
  distribué) ;
- `registrationTtlSeconds = 3 × intervalSeconds` ;
- l'état et le nombre de tâches actives sont fournis à chaque heartbeat.

Côté orchestrateur (`AgentRegistryConfiguration`, actif DISTRIBUTED + ORCHESTRATOR) :
- `InMemoryAgentRegistry` (bean unique satisfaisant `AgentRegistry` et
  `AgentRegistryPort`) ;
- `AgentLifecycleIngestion` s'abonne au `LifecycleChannel` et alimente le registre
  (`registered`, `heartbeat`, `deregistered`, `lost`) ;
- `AgentTtlMonitor` vérifie périodiquement les TTL expirés
  (`agent.registry.ttl-check-interval-seconds`, défaut 10 s) et publie `AgentLost` ;
- `AgentLost` déclenche le fast-fail des steps en cours (ADR-033).

```mermaid
sequenceDiagram
    autonumber
    participant AG as TaskAgentRuntime
    participant LC as LifecycleChannel
    participant ING as AgentLifecycleIngestion
    participant REG as InMemoryAgentRegistry
    participant TTL as AgentTtlMonitor

    AG->>LC: AGENT_REGISTERED(descriptor)
    LC->>ING: onEvent
    ING->>REG: onAgentRegistered
    loop toutes les 10 s
        AG->>LC: AGENT_HEARTBEAT
        LC->>ING: onEvent
        ING->>REG: onAgentHeartbeat
    end
    TTL->>REG: onAgentExpiredIfStillExpired
    TTL-->>AG: AgentLost (Spring event)
    AG->>LC: AGENT_DEREGISTERED
    LC->>ING: onEvent
    ING->>REG: onAgentDeregistered
```

Sans `AgentLifecycleIngestion`, le registre resterait vide et l'orchestrateur ne pourrait
dispatcher aucune tâche.

---

## 8. Multi-claim

Plusieurs agents peuvent être spécialisés pour la même tâche. L'orchestrateur accepte
tous les claims et suit chaque agent indépendamment (ADR-011) :

- chaque agent publie `TaskClaimedByAgent` puis son propre `TaskCompleted`/`TaskFailed` ;
- `TaskCorrelationTracker` enregistre claims, résultats, échecs et ordre de complétion ;
- la tâche est terminée selon `TaskCompletionPolicy`
  (`FIRST_COMPLETE` ou `ALL_COMPLETE`) ;
- le résultat final est sélectionné de façon déterministe
  (`ResultSelectionPolicy`, ADR-034).

Le même `TaskId` peut donc avoir plusieurs `TaskResult` persistés, un par agent.

---

## 9. Restart (`ScenarioRestartSignal`)

```java
public record ScenarioRestartSignal(
    SignalId id,
    ExecutionId executionId,   // null = toutes les exécutions
    String reason,             // ex. "ORCHESTRATOR_RESTART"
    Instant issuedAt
) {}
```

`ScenarioRestartHandler` :
1. appelle `StatefulResourceCleaner.cleanup(executionId)` sur chaque cleaner
   (best-effort ; une erreur n'empêche pas les autres) ;
2. annule les futures actifs correspondant à l'`executionId` (ou tous si null) ;
3. repasse l'agent à IDLE si plus aucune tâche active.

Ressources stateful typiques :

| Exécuteur | Ressources |
|---|---|
| `gatling` | simulation en cours, connexions |
| `mock-server` | WireMock embarqué, mappings |
| `kafka-consumer` / `kafka-producer` | consumer/producer, offsets, flush |
| `database` / `database-assertion` | connexions, transactions |
| `docker` | containers démarrés par l'exécution |
| `shell` | processus fils |

Il n'y a **pas** de reprise : le scénario repart de zéro après restart.

---

## 10. Signaux de cycle de vie START/STOP

`DefaultLifecycleSignalHandler` reçoit `ExecutionLifecycleSignal` :
- START : prépare l'exécution ; pour une assertion `linkedTo`, démarre l'échantillonnage ;
- STOP : termine l'exécution ; pour une assertion `linkedTo`, arrête l'échantillonnage
  selon `stopBehavior`, produit l'`AssertionSummary` final et publie `TaskCompleted`.

Les signaux concernant une tâche non supportée sont ignorés (log DEBUG).
En LOCAL, le dispatcher appelle directement le runtime ; en DISTRIBUTED, le signal passe
par le `SignalChannel` (broadcast).

---

## 11. Configuration

```yaml
agent:
  supported-tasks:            # DISTRIBUTED uniquement — source UNIQUE (ADR-015)
    - database
    - mock-server
    - kafka-producer
    - gatling
    - wiremock
  registry:
    ttl-check-interval-seconds: 10   # orchestrateur
```

| Propriété | Défaut | Portée |
|---|---|---|
| `agent.supported-tasks` | `[]` (agent idle avec WARN) | agent DISTRIBUTED |
| `AGENT_SUPPORTED_TASKS` (env) | — | surcharge la liste (binding Spring) |
| `agent.registry.ttl-check-interval-seconds` | 10 s | orchestrateur |
| heartbeat interval | 10 s (bean distribué) | agent |
| registration TTL | 3 × heartbeat | calculé |
| `taskExecutionTimeout` | 5 min (bean distribué) | agent |

En LOCAL, `agent.supported-tasks` est ignoré : tous les exécuteurs du contexte sont
supportés. `AGENT_TAGS` n'existe plus (ADR-015).

---

## 12. Reconnexion et perte d'agent

1. TTL expiré sans heartbeat → `AgentTtlMonitor` → `AgentLost`.
2. L'orchestrateur invalide les claims de l'agent (`tracker.onAgentLost`).
3. Les steps en cours échouent immédiatement (fast-fail) au lieu d'attendre le timeout.
4. L'agent peut se ré-enregistrer après redémarrage ; il repasse IDLE.
5. Aucune reprise de l'exécution interrompue côté orchestrateur.
