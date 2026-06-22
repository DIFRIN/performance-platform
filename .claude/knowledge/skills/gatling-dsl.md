# Skill — Gatling Java DSL

> Patterns Gatling Java DSL à suivre dans ce projet.
> DSL Scala interdit.

---

## Structure d'une Simulation

```java
public class CustomerApiSimulation extends Simulation {

    // 1. Protocol
    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .shareConnections();

    // 2. Scénario
    private final ScenarioBuilder scn = scenario("Customer API")
        .exec(
            http("POST /customers")
                .post("/api/customers")
                .body(StringBody(CustomerApiSimulation::generatePayload))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("customerId"))
        )
        .pause(100, 500, MILLISECONDS)
        .exec(
            http("GET /customers/{id}")
                .get("/api/customers/#{customerId}")
                .check(status().is(200))
        );

    // 3. Setup (injection injectée dynamiquement par GatlingTaskExecutor)
    {
        setUp(scn.injectOpen(getInjectionSteps()))
            .protocols(httpProtocol)
            .assertions(
                global().failedRequests().percent().lt(1.0),
                global().responseTime().percentile(95).lt(500)
            );
    }

    private static String generatePayload() {
        // Générer des données de test variées
        return """
            {"name": "Customer-%d", "email": "test-%d@example.com"}
            """.formatted(
                ThreadLocalRandom.current().nextInt(100000),
                ThreadLocalRandom.current().nextInt(100000)
            );
    }
}
```

---

## Injecter les OpenInjectionSteps Dynamiquement

```java
// Dans GatlingTaskExecutor, passer les steps via System property ou paramètre Gatling
// La simulation lit la config via System.getProperty() ou via un mécanisme de configuration
```

---

## Feeders

```java
// CSV feeder
FeederBuilder<String> csvFeeder = csv("feeders/customers.csv").circular();

// Random feeder
FeederBuilder<Integer> randomFeeder = listFeeder(
    IntStream.range(1, 1000)
        .mapToObj(i -> Map.of("userId", i))
        .toList()
).random();
```

---

## Assertions Globales Recommandées

```java
.assertions(
    global().failedRequests().percent().lt(1.0),          // < 1% erreurs
    global().responseTime().percentile(95).lt(500),        // p95 < 500ms
    global().responseTime().percentile(99).lt(1000),       // p99 < 1s
    global().requestsPerSec().gt(100.0)                    // > 100 req/s
)
```

---

## Règles du Projet

1. Toujours utiliser `System.getProperty()` pour les URLs (injectées par la plateforme)
2. Toujours inclure des assertions globales dans la simulation
3. Nommer les requêtes HTTP avec des noms métier (pas les URLs brutes)
4. Utiliser `shareConnections()` pour les simulations haute charge
5. Feeders : toujours `.circular()` pour éviter l'épuisement
