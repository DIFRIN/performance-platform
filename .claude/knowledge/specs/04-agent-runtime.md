# Spec 04 — Agent Runtime

**Module** : `platform-agent-runtime`
**Dépend de** : `platform-domain`, `platform-application`, `platform-transport`

---

## 1. Objectif

Un Agent est une instance JVM qui :
1. S'enregistre auprès de l'Orchestrator en déclarant ses spécialisations
2. Reçoit des demandes d'exécution via le transport (broadcast)
3. Filtre localement : exécute si spécialisé, ignore sinon
4. Publie les events (claim, progress, résultat)
5. Maintient un heartbeat
6. Réagit aux signaux de restart orchestrateur (cleanup stateful)

> **Aucune surface d'accès en mode AGENT (ADR-019).** Un nœud AGENT (DISTRIBUTED, `MODE=AGENT`)
> n'expose ni API REST, ni IHM, ni mode CLI : la JVM démarre avec `WebApplicationType.NONE`, donc
> sans aucun serveur web (pas de Tomcat). L'agent est un pur worker piloté par le transport — il ne
> fait que des connexions **sortantes** pour s'enregistrer et recevoir les `TaskExecutionRequest`.
> Seuls LOCAL et ORCHESTRATOR exposent les modes d'accès. Voir la matrice canonique dans
> `specs/00-overview.md` et ADR-019/ADR-021.
>
> Le récepteur de transport HTTP entrant (`agent.http.callbackUrl`, §8 ci-dessous, actif uniquement
> si `transport.type=HTTP`) est un canal interne de réception des tâches du transport — il n'est PAS
> une surface d'accès API/IHM/CLI et ne contredit pas la règle ci-dessus.

---

## 2. Lifecycle de l'Agent

```
OFFLINE
  │  démarrage
  ▼
REGISTERING ──── échec enregistrement (retry) ────▶ OFFLINE
  │  enregistrement réussi
  ▼
IDLE ◀──────────────────────── task terminée ou ignorée
  │  task reçue + spécialisation match
  ▼
EXECUTING
  │  drain (graceful shutdown)
  ▼
DRAINING ──── toutes tâches terminées ────▶ OFFLINE

  [depuis n'importe quel état]
SCENARIO_RESTART signal reçu ──▶ cleanup() ──▶ IDLE
```

---

## 3. Interfaces

```java
public interface AgentRuntime {
    void start();
    void stop();
    AgentState getState();
    AgentDescriptor getDescriptor();

    /**
     * Retourne true si cet agent est spécialisé pour le nom de task donné.
     * Utilisé par TaskSpecializationFilter avant d'accepter une TaskExecutionRequest.
     */
    boolean canExecute(String taskName);

    /**
     * Déclenché par un ScenarioRestartSignal reçu via transport.
     * Doit annuler toute exécution en cours et libérer les ressources stateful
     * (connexions DB, sessions Gatling, WireMock, etc.).
     * L'agent repasse à IDLE après cleanup.
     */
    void onScenarioRestart(ScenarioRestartSignal signal);
}

public interface AgentRegistrationPort {
    void register(AgentDescriptor descriptor) throws RegistrationException;
    void deregister(AgentId agentId);
    void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
}

// Côté Orchestrator
public interface AgentRegistry {
    void onAgentRegistered(AgentDescriptor descriptor);
    void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
    void onAgentExpired(AgentId agentId);      // TTL expiré sans heartbeat
    void onAgentDeregistered(AgentId agentId);

    /**
     * Retourne tous les agents déclarant supportedTaskNames contenant taskName.
     * Pas de sélection — l'orchestrateur ne choisit pas, il vérifie la présence.
     */
    List<AgentDescriptor> findByTaskName(String taskName);
    boolean hasAgentFor(String taskName);
    Optional<AgentDescriptor> findById(AgentId agentId);
    List<AgentDescriptor> findAll();
}
```

---

## 4. AgentDescriptor

```java
public record AgentDescriptor(
    AgentId id,
    String name,
    String host,
    int port,

    /**
     * Endpoint HTTP de l'agent pour le transport HTTP asynchrone.
     * Nullable si transport non-HTTP.
     */
    String httpCallbackUrl,

    /**
     * Liste des noms de tasks que cet agent peut exécuter.
     * Déclarée statiquement à la configuration — immuable après démarrage.
     * Matchée par TaskSpecializationFilter sur le champ StepDefinition.taskName().
     * Un agent "généraliste" déclare explicitement toutes les tasks supportées.
     */
    Set<String> supportedTaskNames,

    AgentCapabilities capabilities,
    AgentState state,
    Instant registeredAt,
    Instant lastHeartbeatAt,

    /**
     * Durée de validité de l'enregistrement sans heartbeat.
     * Après expiration : l'AgentRegistry appelle onAgentExpired().
     * Doit être > heartbeat.intervalSeconds pour éviter les faux positifs.
     * Règle recommandée : ttl >= 3 * heartbeat.intervalSeconds
     */
    Duration registrationTtl
) {}

public record AgentCapabilities(
    int maxConcurrentTasks,
    String version                 // version de l'agent pour compatibilité/observabilité
) {}
```

---

## 5. TaskSpecializationFilter

Composant central côté agent. Décide si l'agent traite ou ignore une `TaskExecutionRequest`.

```java
public interface TaskSpecializationFilter {

    /**
     * Retourne RESPONSIBLE si supportedTaskNames contient request.step().taskName().
     * Retourne NOT_RESPONSIBLE sinon.
     * Appelé pour chaque message reçu depuis le transport (broadcast).
     */
    TaskFilterResult filter(TaskExecutionRequest request);
}

public sealed interface TaskFilterResult
        permits TaskFilterResult.Responsible, TaskFilterResult.NotResponsible {

    record Responsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
    record NotResponsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
}
```

---

## 6. Gestion Multi-Claim (R2)

Plusieurs agents peuvent être spécialisés pour la même task. L'orchestrateur
**accepte tous les claims** et suit l'exécution de chacun indépendamment.

Comportement côté agent :
- Chaque agent spécialisé publie `TaskClaimedByAgent` dès réception
- Chaque agent exécute indépendamment
- Chaque agent publie son propre `TaskCompletedEvent`

Comportement côté orchestrateur :
- `TaskCorrelationTracker.onClaimed()` est appelé pour chaque claim
- Le résultat est stocké sous `context["taskId"]["agentId"]`
- La task est considérée terminée quand **tous** les agents ayant claimé ont complété
  (ou selon `completionPolicy` : FIRST_COMPLETE | ALL_COMPLETE)

```java
public enum TaskCompletionPolicy {
    /**
     * La task est considérée complète dès que le premier agent publie TaskCompleted.
     * Les autres résultats sont stockés s'ils arrivent mais ne bloquent pas l'avancement.
     */
    FIRST_COMPLETE,

    /**
     * La task est considérée complète uniquement quand TOUS les agents ayants claimé
     * ont publié TaskCompleted ou TaskFailed.
     * Utile pour les tests de performance distribués multi-agents.
     */
    ALL_COMPLETE
}
```

---

## 7. ScenarioRestart Signal (R3)

En cas de restart de l'orchestrateur, les exécutions en cours sont abandonnées.
L'orchestrateur publie un `ScenarioRestartSignal` vers tous les agents via le transport.

```java
/**
 * Signal de restart envoyé en broadcast à tous les agents.
 * Les agents doivent :
 * 1. Annuler toute task en cours pour cet executionId (ou tous si executionId=null)
 * 2. Libérer les ressources stateful (connexions, sessions, locks)
 * 3. Repasser à l'état IDLE
 * Note : il n'y a pas de reprise — le scénario repart de zéro après restart.
 */
public record ScenarioRestartSignal(
    SignalId id,
    ExecutionId executionId,       // nullable = tous les scénarios en cours
    String reason,                 // ex: "ORCHESTRATOR_RESTART"
    Instant issuedAt
) {}
```

**Ressources stateful à libérer par type de TaskExecutor** :

| TaskExecutor | Ressources à nettoyer |
|---|---|
| `GatlingTaskExecutor` | Arrêter la simulation en cours, fermer les connexions Gatling |
| `MockServerTaskExecutor` | Arrêter WireMock embedded, réinitialiser les mappings |
| `KafkaConsumerTaskExecutor` | Fermer le consumer, committer les offsets |
| `KafkaProducerTaskExecutor` | Flush + fermer le producer |
| `DatabaseTaskExecutor` | Rollback de toute transaction ouverte, fermer connexion |
| `DockerTaskExecutor` | Arrêter les containers démarrés par cette exécution |
| `ShellTaskExecutor` | Tuer le process fils si toujours en vie |

---

## 8. Configuration Agent

```yaml
agent:
  id: agent-perf-01                # auto-généré UUID si absent
  name: "Performance Test Agent"

  supportedTasks:                  # STATIQUE — immuable après démarrage, source EXCLUSIVE de supportedTaskNames (ADR-015)
    - performance_test
    - gatling-metric
    - assertions
    # NOTE (ADR-015) : supportedTaskNames est derive EXCLUSIVEMENT de cette config.
    # Les annotations @Preparation/@Injection/@Assertion sont utilisees UNIQUEMENT
    # par PluginLoader (task-name -> implementation) et NE contribuent PAS a supportedTaskNames.

  capabilities:
    maxConcurrentTasks: 3
    version: "1.0.0"

  task:
    completionPolicy: ALL_COMPLETE  # FIRST_COMPLETE | ALL_COMPLETE

  heartbeat:
    intervalSeconds: 10
    # NOTE R6 : ttlSeconds doit satisfaire ttlSeconds >= 3 * intervalSeconds.
    # Si l'intervalle est trop court par rapport au TTL, des faux positifs d'expiration
    # peuvent survenir sous charge. Ajuster en fonction de la latence réseau observée.
    ttlSeconds: 30                  # orchestrator marque offline si dépassé

  orchestrator:
    url: http://orchestrator:8080
    reconnect:
      maxAttempts: -1               # -1 = infini
      backoffSeconds: 5
      maxBackoffSeconds: 60

  http:
    # Endpoint de callback pour le transport HTTP asynchrone.
    # L'orchestrateur envoie la TaskExecutionRequest ici (POST).
    # L'agent répond 202 Accepted puis publie les events via POST /api/v1/events.
    callbackUrl: http://agent-perf-01:9090/callbacks
```

---

## 9. Comportement de Reconnexion

1. TTL expiré sans heartbeat → `AgentRegistry.onAgentExpired(agentId)` côté orchestrateur
2. Orchestrateur publie `ScenarioRestartSignal` pour les exécutions impactées
3. Les tasks en cours sur cet agent passent à FAILED (côté orchestrateur)
4. Retry sur un autre agent spécialisé si disponible (configurable)
5. Agent restart → re-enregistrement → IDLE

---

## 10. LocalAgent (Mode LOCAL)

En mode LOCAL, un unique `LocalAgent` déclare l'ensemble des spécialisations disponibles.
Il utilise un transport in-memory. Le workflow est identique au mode DISTRIBUTED.

```java
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalAgent implements AgentRuntime {

    // supportedTaskNames = tous les noms déclarés dans TaskExecutorRegistry
    // Transport = InMemoryExecutionTransport (queue en mémoire)
    // Filter = toujours RESPONSIBLE (supporte tout)
    // completionPolicy = FIRST_COMPLETE (un seul agent)
}
```
