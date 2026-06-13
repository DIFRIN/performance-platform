# Spec 06 — Injection Gatling

**Module** : `platform-injection-gatling`  
**Dépend de** : `platform-domain`, `platform-application`

---

## 1. Objectif

Exécuter des simulations Gatling depuis la plateforme, collecter les métriques
et retourner un `InjectionResult` complet dans l'`ExecutionContext`.

**DSL Gatling utilisé : Java DSL uniquement (pas Scala).**

---

## 2. GatlingTaskExecutor

```java
@Injection(name = "gatling", description = "Gatling load injection")
public class GatlingTaskExecutor implements TaskExecutor {

    @Override
    public String getSupportedTaskName() { return "gatling"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        // 1. Extraire les paramètres depuis step.parameters()
        // 2. Résoudre le LoadModel depuis step.parameters().get("loadModel")
        // 3. Configurer la simulation Gatling
        // 4. Lancer via GatlingRunner
        // 5. Parser les résultats
        // 6. Retourner TaskResult avec InjectionResult dans outputs
        //    L'orchestrateur mergera dans context[step.id()][agentId]
    }
}
```

---

## 3. GatlingRunner

```java
public interface GatlingRunner {
    /**
     * Lance une simulation Gatling en-process.
     * Retourne le chemin du répertoire de résultats.
     */
    Path run(GatlingRunConfig config) throws GatlingExecutionException;
}

public record GatlingRunConfig(
    String simulationClass,          // nom qualifié de la classe
    LoadModel loadModel,
    Map<String, String> systemProperties,
    Path resultsDirectory,
    String simulationId,
    Duration timeout
) {}
```

---

## 4. LoadModel → Gatling OpenInjection

Chaque `LoadModelType` se traduit en OpenInjectionStep Gatling Java DSL :

```java
public interface LoadModelTranslator {
    List<OpenInjectionStep> translate(LoadModel model);
}

// Implémentations :

// RAMP → rampUsersPerSec(from).to(to).during(duration) par stage
// CONSTANT → constantUsersPerSec(n).during(duration)
// SPIKE → rampUsersPerSec(base).to(spike).during(spikeDuration)
// STAIR → stressPeakUsers(n).during(duration) ou stairs custom
// SOAK → constantUsersPerSec(n).during(duration) avec ramp initial
// BURST → Séquence de nothingFor() + atOnceUsers() répétée
// RAMP_UP_DOWN → rampUp + hold + rampDown
// CUSTOM → interpolation linéaire entre les points
```

---

## 5. InjectionResult

```java
public record InjectionResult(
    TaskId taskId,
    String simulationClass,
    Duration duration,
    long totalRequests,
    long successfulRequests,
    long failedRequests,
    double errorRate,               // en pourcentage : 0.0 - 100.0
    double throughput,              // requests/second
    long p50Ms,
    long p75Ms,
    long p90Ms,
    long p95Ms,
    long p99Ms,
    long maxMs,
    long minMs,
    double meanMs,
    Path gatlingReportDirectory,    // répertoire du rapport HTML Gatling
    Map<String, Object> rawStats    // stats brutes pour assertions custom
) {}
```

---

## 6. Stockage dans ExecutionContext

L'executor retourne l'`InjectionResult` dans `TaskResult.outputs` sous la clé `"result"`.
L'orchestrateur merge dans `context[step.id()][agentId]` après réception.

```java
// Dans GatlingTaskExecutor.execute() — retourner dans TaskResult.outputs
return TaskResult.success(
    TaskId.of(step.id().value()),
    getSupportedTaskName(),
    elapsed,
    Map.of("result", injectionResult)
);
// L'orchestrateur fait ensuite : context.with(step.id(), Map.of(agentId, taskResult))
```

---

## 7. Protocoles Supportés

| Protocole | Classe de config Gatling | Dépendance Maven |
|---|---|---|
| HTTP/HTTPS | `HttpProtocolBuilder` | `gatling-http` |
| WebSocket | `HttpProtocolBuilder` (ws) | `gatling-http` |
| Kafka | `KafkaProtocolBuilder` | `gatling-kafka` |
| JMS | `JmsProtocolBuilder` | `gatling-jms` |
| gRPC | `GrpcProtocolBuilder` | `gatling-grpc` |

---

## 8. Exemple Simulation Java DSL Référence

```java
public class CustomerApiSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://customer-api:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    private final ScenarioBuilder scn = scenario("Customer API")
        .exec(
            http("Create Customer")
                .post("/api/customers")
                .body(StringBody(session -> generateCustomerJson()))
                .check(status().is(201))
        );

    // La liste des OpenInjectionStep est injectée par GatlingTaskExecutor
    // via le mécanisme de configuration Gatling
    {
        setUp(scn.injectOpen(/* injecté dynamiquement */))
            .protocols(httpProtocol);
    }
}
```

---

## 9. Parsing des Résultats Gatling

Les résultats Gatling sont dans `simulation.log` (format interne) et dans le
répertoire `results/<simId>/`. Parser :

1. `stats.json` (généré par Gatling) → métriques globales
2. `simulation.log` → stats par requête si nécessaire

```java
public interface GatlingResultParser {
    InjectionResult parse(Path gatlingResultDirectory, TaskId taskId) throws ResultParsingException;
}
```
