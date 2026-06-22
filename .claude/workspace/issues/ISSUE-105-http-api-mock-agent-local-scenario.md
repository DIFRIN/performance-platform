# ISSUE-105 — Create http-api-mock-agent-local.yaml scenario (LOCAL mode)

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : APPROVED
**Priorite** : P1 (critique — scenario de demonstration principal)
**Bloquee par** : ISSUE-103 (wiremock retire du compose, le mock est maintenant agent-embedded)
**Estime** : M (1-3h)

---

## Objectif

Creer un scenario YAML complet qui demontre le pattern "WireMock as Agent" en mode LOCAL. Le scenario montre :

1. Phase PREPARATION : l'agent local demarre un WireMock EMBEDDED (mock-server, port 0 auto-assign, mappings charges depuis classpath)
2. Phase INJECTION : Gatling envoie du trafic HTTP vers le mock via `target: mock-api`
3. Phase ASSERTION : verification des metriques Gatling (response time, success rate) + verification que le mock a recu le nombre attendu de requetes
4. Rapport genere en fin d'execution

**En mode LOCAL**, une seule instance JVM execute tout : orchestrateur + agent + tous les TaskExecutors. La specialisation des agents est non-pertinente — tous les tasks sont disponibles par definition. Aucun dispatch, aucun filtrage. Le scenario ne contient PAS de champ `agentTags` (le routing par tags n'a aucun sens en LOCAL).

## Fichiers a Creer

```
platform-deployment/examples/scenarios/
  └── http-api-mock-agent-local.yaml       — Scenario complet LOCAL mock-as-agent

platform-deployment/examples/mappings/
  └── http-api-endpoints.json              — Stubs WireMock pour le demo
```

## http-api-mock-agent-local.yaml — Contenu

```yaml
# =============================================================================
# Scenario : HTTP API Test — Mock as Agent (LOCAL)
# =============================================================================
# Demontre le pattern "WireMock as Agent" en mode LOCAL :
#   - Une seule instance JVM = orchestrateur + agent + tous les TaskExecutors
#   - L'agent demarre WireMock in-process (EMBEDDED, port auto-assign)
#   - Gatling injecte du trafic HTTP contre le mock
#   - Assertions verifient les metriques Gatling + compteurs mock
#
# En LOCAL : tout est dans la meme JVM. La specialisation des agents est
# non-pertinente — LocalAgent declare tous les supportedTaskNames.
# Aucun agentTags dans le scenario (pas de routing distribue).
#
# Prerequis :
#   java -jar platform-app.jar --spring.profiles.active=local,examples-local
#
# Config technique dans application-examples-local.yaml :
#   platform.http-targets.mock-api.base-url: resolu dynamiquement depuis
#   ExecutionContext (port auto-assign par MockServerTaskExecutor)
# =============================================================================
scenario:
  id: http-api-mock-agent-local
  name: "HTTP API Performance Test — Mock as Agent (LOCAL)"
  version: "1.0.0"
  tags: [demo, mock-agent, http, local]
  metadata:
    owner: perf-team
    description: "Demo: WireMock demarre en tant qu'agent (EMBEDDED), Gatling injecte, assertions verifient"
  execution:
    mode: LOCAL

  steps:
    # Step 1 — Preparation : Demarrer WireMock in-process (agent local)
    # Port: 0 = auto-assign. Outputs stockes dans ExecutionContext["spawn-mock"].
    # Pas de agentTags — en LOCAL, tous les tasks sont disponibles.
    - id: spawn-mock
      task: mock-server
      phase: PREPARATION
      parameters:
        deployment: EMBEDDED
        action: START
        port: 0
        mappingsPath: classpath:mock/mappings
      timeout: 30s

    # Step 2 — Injection : Gatling simule 100 users pendant 60s
    # target: mock-api -> resolution via HttpTargetRegistry vers l'URL du mock
    - id: load-test
      task: gatling
      phase: INJECTION
      dependsOn: [spawn-mock]
      requiredContexts: [spawn-mock]
      parameters:
        target: mock-api
        simulation: HttpApiSimulation
        path: /api/users
        method: GET
      timeout: 5m

    # Step 3 — Assertion : Verifier le taux d'erreur Gatling < 1%
    - id: assert-error-rate
      task: gatling-metric
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        metric: errorRate
        operator: LT
        value: 1.0
      timeout: 30s

    # Step 4 — Assertion : Verifier le temps de reponse p99 < 500ms
    - id: assert-response-time
      task: gatling-metric
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        metric: p99ResponseTime
        operator: LT
        value: 500
      timeout: 30s

    # Step 5 — Assertion : Verifier le taux de succes > 99%
    - id: assert-success-rate
      task: gatling-metric
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        metric: successRate
        operator: GT
        value: 99.0
      timeout: 30s

    # Step 6 — Assertion : Verifier que le mock a recu >= 100 requetes
    # Mode EMBEDDED -> MockServerTaskExecutor VERIFY compte les requetes.
    - id: assert-mock-hit
      task: http-mock
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        target: mock-api
        urlPattern: "/api/users"
        expectedRequestCount: 100
        operator: GREATER_THAN_OR_EQUAL
      timeout: 1m

    # Step 7 — Cleanup : Arreter le mock (cleanup stateful agent)
    - id: stop-mock
      task: mock-server
      phase: PREPARATION
      dependsOn: [assert-mock-hit, assert-error-rate, assert-response-time, assert-success-rate]
      requiredContexts: [assert-mock-hit]
      parameters:
        deployment: EMBEDDED
        action: STOP
      timeout: 10s

loadModels:
  demo-ramp:
    type: RAMP
    target: 100
    duration: 60s
```

## http-api-endpoints.json — Stubs WireMock

```json
{
  "mappings": [
    {
      "name": "GET /api/users",
      "priority": 1,
      "request": {
        "method": "GET",
        "urlPattern": "/api/users.*"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": {
          "users": [
            { "id": 1, "name": "Alice" },
            { "id": 2, "name": "Bob" }
          ],
          "total": 2,
          "page": 1
        }
      }
    },
    {
      "name": "GET /api/users/:id",
      "priority": 1,
      "request": {
        "method": "GET",
        "urlPattern": "/api/users/[0-9]+"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": {
          "id": 1,
          "name": "Alice",
          "email": "alice@example.com"
        }
      }
    }
  ]
}
```

## Regles Specifiques

- **Aucun `agentTags`** : Le scenario est en mode LOCAL. La specialisation des agents est non-pertinente. `LocalAgent` declare tous les `supportedTaskNames` (tous les noms du `TaskExecutorRegistry`). Aucun dispatch, aucun filtrage.
- **Aucune URL inline** : utiliser `target: mock-api` (HttpTargetRegistry) et `classpath:mock/mappings` (classpath reference)
- **Port auto-assign** : `port: 0` — l'agent choisit un port libre, pas de collision
- **Mapping path classpath** : `classpath:mock/mappings` — les fichiers JSON de stubs sont dans le JAR
- **Pipeline complet** : spawn-mock -> load-test -> assertions -> stop-mock (cleanup)
- Le step `stop-mock` est en phase PREPARATION mais execute APRES les assertions (grace au DAG dependsOn). Ce n'est pas une violation de la phase — le DAG determine l'ordre d'execution reel.
- Le repertoire `platform-deployment/examples/mappings/` doit etre cree s'il n'existe pas
- **Configuration-driven model** : Les `supportedTaskNames` de `LocalAgent` viennent du `TaskExecutorRegistry` (tous les noms enregistres). En LOCAL, tout est disponible — c'est la nature du mode.

## Criteres de Done

- [ ] `http-api-mock-agent-local.yaml` cree dans `platform-deployment/examples/scenarios/`
- [ ] `http-api-endpoints.json` cree dans `platform-deployment/examples/mappings/`
- [ ] Le scenario est parseable par le ScenarioParser (test unitaire)
- [ ] Scenario contient 0 URL inline — uniquement `target:` et `classpath:` references
- [ ] Aucun champ `agentTags` dans le scenario
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-105 -> DONE
