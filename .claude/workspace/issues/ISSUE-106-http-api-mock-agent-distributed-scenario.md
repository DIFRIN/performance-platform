# ISSUE-106 — Create http-api-mock-agent-distributed.yaml scenario (DISTRIBUTED mode)

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : WAITING
**Priorite** : P1 (critique — contrepartie DISTRIBUTED du scenario LOCAL)
**Bloquee par** : ISSUE-105 (les mappings JSON sont deja crees, scenario LOCAL sert de modele)
**Estime** : M (1-3h)

---

## Objectif

Creer un scenario YAML pour le mode DISTRIBUTED qui demontre le pattern "WireMock as Agent" avec des agents specialises via le modele configuration-driven. Le scenario montre la distribution des roles par task name :

1. **mock-agent** (agent.supported-tasks: [mock-server, http-client]) — demarre WireMock EMBEDDED en phase PREPARATION
2. **gatling-agent** (agent.supported-tasks: [gatling, gatling-metric]) — execute l'injection de charge HTTP en phase INJECTION
3. **standard-agent** (agent.supported-tasks: [http-mock, database, kafka]) — execute les assertions

**Routing** : L'orchestrateur broadcast chaque `TaskExecutionRequest`. Chaque agent filtre localement via `TaskSpecializationFilter` base sur `supportedTaskNames` (issu de `agent.supported-tasks`). Aucun `agentTags` dans le scenario — le routing est par `task:` name UNIQUEMENT.

## Fichier a Creer

```
platform-deployment/examples/scenarios/
  └── http-api-mock-agent-distributed.yaml
```

## http-api-mock-agent-distributed.yaml — Contenu

```yaml
# =============================================================================
# Scenario : HTTP API Test — Mock as Agent (DISTRIBUTED)
# =============================================================================
# Demontre le pattern "WireMock as Agent" en mode distribue :
#   - mock-agent demarre WireMock EMBEDDED (port auto-assign)
#   - gatling-agent execute l'injection de charge HTTP contre le mock
#   - standard-agent execute les assertions (gatling-metrics + http-mock)
#
# Routing : broadcast par l'orchestrateur, filtrage cote agent via
# TaskSpecializationFilter base sur agent.supported-tasks (configuration-driven).
# Aucun agentTags dans le scenario — le routing est par task name uniquement.
#
# Configuration des agents (dans docker-compose ou application.yml) :
#   mock-agent:     agent.supported-tasks: [mock-server, http-client]
#   gatling-agent:  agent.supported-tasks: [gatling, gatling-metric]
#   standard-agent: agent.supported-tasks: [http-mock, database, kafka]
#
# Prerequis :
#   docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml up -d
#
# Config technique :
#   platform.http-targets.mock-api.base-url: auto-resolved from ExecutionContext
#   Les URLs sont resolues dynamiquement via TaskResult.outputs de spawn-mock
# =============================================================================
scenario:
  id: http-api-mock-agent-distributed
  name: "HTTP API Performance Test — Mock as Agent (DISTRIBUTED, 500 users, 120s ramp)"
  version: "1.0.0"
  tags: [demo, mock-agent, http, distributed]
  metadata:
    owner: perf-team
    description: "Demo DISTRIBUTED: mock-agent demarre WireMock, gatling-agent injecte, standard-agent verifie"
  execution:
    mode: DISTRIBUTED

  steps:
    # Step 1 — Preparation : Demarrer WireMock sur mock-agent
    # Routing : task=mock-server -> mock-agent (agent.supported-tasks contient mock-server)
    # Port: 0 = auto-assign. L'agent publie port+url dans TaskResult.outputs.
    - id: spawn-mock
      task: mock-server
      phase: PREPARATION
      parameters:
        deployment: EMBEDDED
        action: START
        port: 0
        mappingsPath: classpath:mock/mappings
      timeout: 30s

    # Step 2 — Injection : Gatling 500 users, ramp 120s
    # Routing : task=gatling -> gatling-agent (agent.supported-tasks contient gatling)
    - id: load-test
      task: gatling
      phase: INJECTION
      dependsOn: [spawn-mock]
      requiredContexts: [spawn-mock]
      parameters:
        target: mock-api
        simulation: HttpApiHighLoadSimulation
        path: /api/users
        method: GET
      timeout: 5m

    # Step 3 — Assertion : Verifier taux d'erreur Gatling < 1%
    # Routing : task=gatling-metric -> gatling-agent ou standard-agent
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

    # Step 4 — Assertion : Verifier le taux de succes > 99%
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

    # Step 5 — Assertion : Verifier le temps de reponse p99 < 1000ms
    - id: assert-response-time
      task: gatling-metric
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        metric: p99ResponseTime
        operator: LT
        value: 1000
      timeout: 30s

    # Step 6 — Assertion : Verifier requetes recues par le mock
    # Routing : task=http-mock -> standard-agent (agent.supported-tasks contient http-mock)
    # Mode EXTERNAL (l'assertion agent interroge le mock-agent a distance)
    - id: assert-mock-hit
      task: http-mock
      phase: ASSERTION
      dependsOn: [load-test]
      requiredContexts: [load-test]
      parameters:
        target: mock-api
        urlPattern: "/api/users"
        expectedRequestCount: 50000
        operator: GREATER_THAN_OR_EQUAL
      timeout: 1m

    # Step 7 — Cleanup : Arreter le mock (cleanup stateful agent)
    # Routing : task=mock-server -> mock-agent
    - id: stop-mock
      task: mock-server
      phase: PREPARATION
      dependsOn: [assert-mock-hit, assert-error-rate, assert-success-rate, assert-response-time]
      requiredContexts: [assert-mock-hit]
      parameters:
        deployment: EMBEDDED
        action: STOP
      timeout: 10s

loadModels:
  demo-high-ramp:
    type: RAMP
    target: 500
    duration: 120s
```

## Regles Specifiques

- **Aucun `agentTags`** : Le routing est entierement par `task:` name. L'orchestrateur broadcast la `TaskExecutionRequest`. Chaque agent filtre via `TaskSpecializationFilter` base sur `supportedTaskNames` (issu de `agent.supported-tasks` dans la configuration). Les tags ne sont plus utilises pour le routing.
- **Configuration-driven model** :
  - mock-agent config : `agent.supported-tasks: [mock-server, http-client]`
  - gatling-agent config : `agent.supported-tasks: [gatling, gatling-metric]`
  - standard-agent config : `agent.supported-tasks: [http-mock, database, kafka]`
- **Aucune URL inline** : utiliser `target: mock-api` resolue via HttpTargetRegistry
- **Le step stop-mock** est en phase PREPARATION mais execute APRES les assertions grace au DAG. Ceci est intentionnel — cleanup stateful apres la campagne.
- **Charge plus elevee** que le mode LOCAL (500 users, 120s ramp, 50000 requetes attendues)
- **requiredContexts** : chaque step declare ce dont il a besoin -> le PartialExecutionContext envoye a l'agent contient uniquement les entrees necessaires
- Les mappings JSON sont les memes que ceux crees par ISSUE-105 (`http-api-endpoints.json`)
- **Multi-claim possible** : Si deux agents declarent la meme task dans `agent.supported-tasks` (ex: `gatling-metric` dans les deux gatling-agent et standard-agent), les deux peuvent claimer et executer. Voir `TaskCompletionPolicy` et ADR-011.

## Criteres de Done

- [ ] `http-api-mock-agent-distributed.yaml` cree dans `platform-deployment/examples/scenarios/`
- [ ] Le scenario est parseable par le ScenarioParser (test unitaire)
- [ ] Scenario contient 0 URL inline — uniquement `target:`, `classpath:` references logiques
- [ ] Aucun champ `agentTags` dans le scenario
- [ ] Commentaires documentent le routing par task name + config `agent.supported-tasks`
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-106 -> DONE
