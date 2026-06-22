# Exemples — Performance Engineering Platform

Deux types de démos :
- **Demo 1: IoT SUT** — use cases réels avec services SUT (iot-dispatcher, device-api)
- **Demo 2: Mock as Agent** — WireMock démarré EMBEDDED par un agent spécialisé

---

## Modèle de Spécialisation des Agents (Configuration-Driven)

La plateforme utilise un modèle **entièrement configuration-driven** pour la spécialisation des agents :

```yaml
# application.yml (ou variable d'environnement AGENT_SUPPORTED_TASKS)
agent:
  supported-tasks:
    - mock-server
    - http-client
```

**Comment ça marche :**
1. Chaque agent déclare explicitement ses `supported-tasks` dans sa configuration
2. Au démarrage, l'agent construit `AgentDescriptor.supportedTaskNames` depuis cette config
3. L'orchestrateur broadcast chaque `TaskExecutionRequest` à tous les agents (pas de sélection ciblée)
4. Chaque agent filtre localement via `TaskSpecializationFilter` : si la task est dans `supportedTaskNames`, l'agent l'exécute ; sinon, il l'ignore

**Ce qui N'EST PAS utilisé pour le routing :**
- Les annotations `@Preparation`/`@Injection`/`@Assertion` ne dérivent PAS les `supportedTaskNames` — elles servent uniquement au PluginLoader pour la résolution task-name → implémentation
- Les `agentTags` sont COMPLÈTEMENT SUPPRIMÉS — aucun routing par tags
- Pas d'auto-discovery des tâches disponibles sur un agent

### Mode LOCAL vs DISTRIBUTED

| Aspect | LOCAL | DISTRIBUTED |
|---|---|---|
| Instances JVM | 1 seule (tout in-process) | Plusieurs (orchestrateur + N agents) |
| Spécialisation | Non-pertinente — LocalAgent a tous les supportedTaskNames | Critique — chaque agent a sa liste explicite |
| `agent.supported-tasks` | Ignoré en pratique (tout est disponible) | Obligatoire — définit le rôle de l'agent |
| `agentTags` dans les scénarios | Non (pas de dispatch) | Non (supprimé — routing par task name) |
| Configuration | `--spring.profiles.active=local` | `MODE=AGENT` + `AGENT_SUPPORTED_TASKS=...` |
| Dispatch | Aucun — exécution directe in-process | Broadcast Kafka vers tous les agents |
| Filtrage | `LocalAgent.canExecute()` retourne toujours `true` | `TaskSpecializationFilter` vérifie `supportedTaskNames` |

---

## Architecture

### Demo 1: IoT SUT

```
┌─────────────────────┐        ┌──────────────────────────────────┐
│  SUT (docker-compose│        │  Plateforme Performance           │
│       -sut.yaml)    │        │  (docker-compose.yaml ou JVM)    │
│                     │        │                                   │
│  iot-dispatcher:8083│◄───────│  kafka-producer (préparation)     │
│  device-api    :8084│◄───────│  gatling        (injection)       │
│  kafka-sut     :9093│◄──────►│  kafka assertion, http-mock       │
│  postgres-sut  :5433│◄──────►│  database assertion               │
└─────────────────────┘        └──────────────────────────────────┘
```

### Demo 2: Mock as Agent

```
┌──────────────────────────────────────────┐
│  WireMock as Agent (DISTRIBUTED)         │
│  docker-compose-wiremock-agent.yaml      │
│                                          │
│  ┌─────────────┐   ┌──────────────────┐  │
│  │ orchestrator │   │   mock-agent     │  │
│  │  :8080       │   │ WireMock EMBEDDED│  │
│  └──────┬───────┘   │ port dyn.        │  │
│         │           └────────┬─────────┘  │
│    ┌────┴────┐               │            │
│    │  kafka  │        ┌──────┴──────┐    │
│    │  :9092  │        │  gatling-   │    │
│    └────┬────┘        │  agent      │    │
│         │             │  :8081      │    │
│    ┌────┴────┐        └──────┬──────┘    │
│    │ postgres │              │            │
│    │ :5432    │       ┌──────┴──────┐    │
│    └─────────┘       │  standard-  │    │
│                      │  agent      │    │
│                      │  :8082      │    │
│                      └─────────────┘    │
└──────────────────────────────────────────┘

Pas de service WireMock standalone — le mock est un agent spécialisé.
Chaque agent a une config AGENT_SUPPORTED_TASKS explicite.
Même image Docker partout, config différente = rôle différent.
```

---

## Prérequis

- Docker + Docker Compose v2
- Java 25 + Maven 3.9+ (pour builder l'image)
- 4 Go RAM disponibles

---

## Demo 1: IoT SUT (SUT-A iot-dispatcher + SUT-B device-api)

### Démarrage du SUT

```bash
# Depuis la racine du projet
docker compose -f platform-deployment/examples/docker-compose-sut.yaml up -d

# Vérifier que tout est healthy (~60s)
docker compose -f platform-deployment/examples/docker-compose-sut.yaml ps
```

Ports exposés :
| Service | Port | Description |
|---|---|---|
| kafka-sut | 9093 | Kafka SUT (topics: iot-commands, device-events) |
| postgres-sut | 5433 | PostgreSQL SUT (10k devices pré-chargés) |
| iot-dispatcher | 8083 | SUT-A : consomme Kafka → dispatch HTTP |
| device-api | 8084 | SUT-B : reçoit HTTP → publie Kafka |

### Mode LOCAL (un seul process)

```bash
# Builder la plateforme
mvn clean package -DskipTests -q

# Démarrer en mode LOCAL avec le profil d'exemples
java -jar platform-app/target/performance-platform.jar \
  --spring.profiles.active=local,examples-local
```

> La plateforme se connecte au SUT via `localhost:9093`, `localhost:5433`, etc.

### Mode DISTRIBUTED (orchestrateur + agents)

```bash
# Construire l'image Docker de la plateforme
docker build -f platform-deployment/docker/Dockerfile -t performance-platform:latest .

# Démarrer orchestrateur + 2 agents
docker compose -f platform-deployment/docker/docker-compose.yaml up -d

# Vérifier
curl http://localhost:8080/actuator/health
```

> En mode DISTRIBUTED, la plateforme utilise `host.docker.internal:9093` pour accéder au SUT.
> Modifier `application-orchestrator.yaml` : `IOT_KAFKA_SERVERS=host.docker.internal:9093`

### Lancer les Scénarios

#### SUT-A : IoT Dispatcher

```bash
# Mode LOCAL (1 000 commandes)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/iot-dispatcher-local.yaml

# Mode DISTRIBUTED (10 000 commandes)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/iot-dispatcher-distributed.yaml
```

#### SUT-B : Device API

```bash
# Mode LOCAL (1 000 users, ramp 60s)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/device-api-local.yaml

# Mode DISTRIBUTED (10 000 users, ramp 120s)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/device-api-distributed.yaml
```

#### Suivre l'exécution

```bash
# Status de l'exécution
curl http://localhost:8080/executions/{execution-id}/status

# Logs plateforme (mode LOCAL)
# → stdout de la JVM

# Logs plateforme (mode DISTRIBUTED)
docker compose -f platform-deployment/docker/docker-compose.yaml logs orchestrator -f
```

### Adapter pour votre Environnement

Toute la configuration technique est dans `application-examples-local.yaml` ou `application-orchestrator.yaml`.
Les scénarios YAML ne contiennent aucune URL ou nom de topic — uniquement des références logiques.

```yaml
# Pour changer les noms de topics (ex: env de prod)
# Dans application-prod.yaml :
platform:
  kafka-clusters:
    iot-sut:
      bootstrap-servers: kafka-prod.internal:9092
      topics:
        iot-commands: iot-commands-prod-v3    # ← seul changement nécessaire
        device-events: device-events-prod
  http-targets:
    device-api:
      base-url: http://device-api.prod.internal:8084
      paths:
        submit-event: /api/v2/events          # ← si l'API a changé de version
```

Le même scénario `device-api-local.yaml` s'exécute sans modification sur tous les environnements.

---

## Demo 2: WireMock as Agent (Mock Server)

Cette démo montre comment un agent spécialisé démarre un WireMock EMBEDDED
en réponse à un step de préparation. Pas de service WireMock standalone.

### Spécialisation des agents (configuration-driven)

Chaque agent déclare ses tâches via la configuration, pas via des annotations ou des tags.

**mock-agent** — `AGENT_SUPPORTED_TASKS=mock-server,http-client` :
- Démarre WireMock in-process (EMBEDDED), port auto-assigné
- Mappings chargés depuis le classpath
- Peut aussi agir comme client HTTP

**gatling-agent** — `AGENT_SUPPORTED_TASKS=gatling,gatling-metric` :
- Exécute les simulations d'injection de charge HTTP avec Gatling
- Peut aussi évaluer les métriques Gatling (assertions gatling-metric)

**standard-agent** — `AGENT_SUPPORTED_TASKS=http-mock,database,kafka` :
- Exécute les assertions (gatling-metric, http-mock)
- Interroge le mock-agent à distance pour vérifier les compteurs
- Peut exécuter des opérations database et kafka

### Démarrage

```bash
# 1. Builder l'image Docker de la plateforme
docker build -f platform-deployment/docker/Dockerfile -t performance-platform:latest .

# 2. Démarrer la plateforme avec agents spécialisés
docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml up -d

# 3. Vérifier que tout est healthy (~90s)
curl http://localhost:8080/actuator/health
```

### Lancer les scénarios

```bash
# Mode LOCAL (tout in-process, MockServer EMBEDDED)
# Spécialisation non-pertinente — LocalAgent a tous les supportedTaskNames
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/http-api-mock-agent-local.yaml

# Mode DISTRIBUTED (agents spécialisés par configuration)
# Routing par task name — broadcast + TaskSpecializationFilter côté agent
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/http-api-mock-agent-distributed.yaml
```

### Déroulement du workflow (DISTRIBUTED)

1. L'orchestrateur reçoit le scénario et planifie le DAG
2. Step `spawn-mock` (task: mock-server) → dispatch broadcast
3. Chaque agent reçoit la `TaskExecutionRequest` et filtre via `TaskSpecializationFilter`
4. mock-agent filtre → RESPONSIBLE (mock-server est dans supportedTaskNames) → démarre WireMock EMBEDDED
5. gatling-agent et standard-agent filtrent → NOT_RESPONSIBLE → ignorent
6. mock-agent publie `TaskCompleted` avec outputs `{port: 35421, url: "http://mock-agent:35421"}`
7. Step `load-test` (task: gatling) → dispatch broadcast → seul gatling-agent accepte
8. Gatling envoie du trafic HTTP vers `http://mock-agent:35421/api/users`
9. Steps d'assertion (gatling-metric, http-mock) → dispatch broadcast → standard-agent accepte
10. standard-agent vérifie métriques Gatling + compteurs WireMock
11. Step `stop-mock` (task: mock-server) → dispatch broadcast → mock-agent accepte → arrête proprement WireMock

### Rapports

Les rapports sont générés dans le volume `./reports/` (monté sur l'orchestrateur) :
- `./reports/{executionId}/report.html` — rapport HTML
- `./reports/{executionId}/report.json` — rapport JSON
- `./reports/{executionId}/report.pdf` — rapport PDF

---

## Arrêter

```bash
# Arrêter le SUT IoT
docker compose -f platform-deployment/examples/docker-compose-sut.yaml down -v

# Arrêter la plateforme Mock Agent
docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml down -v
```
