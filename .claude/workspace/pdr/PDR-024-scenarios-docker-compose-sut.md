# PDR-024 — Scénarios IoT + Docker Compose SUT

**Module Maven** : `platform-deployment/examples/` (fichiers de déploiement et scénarios DSL — pas de code Java)
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/01-scenario-dsl.md`, `.claude/knowledge/specs/09-deployment.md`
**Dépend de** : PDR-020 DONE (cluster: ref dans scénarios), PDR-022 DONE (target: ref dans scénarios), PDR-023 DONE (services SUT buildables)
**Issues** : ISSUE-099, ISSUE-100, ISSUE-101, ISSUE-102

---

## Responsabilité

Crée les fichiers de démonstration complets pour les deux use cases IoT :
1. **`docker-compose-sut.yaml`** — lance les services SUT indépendamment de la plateforme
2. **Scénarios YAML** — un scénario par use case × mode plateforme (LOCAL / DISTRIBUTED)
3. **README** — guide de démarrage step-by-step

### Principe d'isolation SUT / Plateforme

```
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│  docker-compose-sut.yaml        │    │  docker-compose.yaml (existant) │
│  (lancé séparément par l'user)  │    │  (plateforme)                   │
│                                 │    │                                 │
│  iot-dispatcher :8083           │◄───│  agents (Gatling, kafka-prod...) │
│  device-api     :8084           │    │  orchestrateur :8080             │
│  kafka-sut      :9093           │    │                                 │
│  postgres-sut   :5433           │    │  platform.kafka-clusters:        │
│  wiremock       :8090           │    │    iot-sut.bootstrap: :9093      │
└─────────────────────────────────┘    │  platform.http-targets:          │
                                       │    device-api.base-url: :8084    │
                                       │    wiremock.base-url: :8090      │
                                       └─────────────────────────────────┘
```

Le SUT expose ses ports sur localhost. La plateforme (en LOCAL ou DISTRIBUTED) s'y connecte via `localhost` (si elle tourne en JVM directe) ou via `host.docker.internal` (si elle tourne dans Docker).

---

## Structure des Fichiers

```
platform-deployment/examples/
├── docker-compose-sut.yaml
├── wiremock/
│   └── mappings/
│       └── iot-command.json          WireMock stub POST /command → 200 OK
├── scenarios/
│   ├── iot-dispatcher-local.yaml     SUT-A, plateforme en mode LOCAL
│   ├── iot-dispatcher-distributed.yaml  SUT-A, plateforme en mode DISTRIBUTED
│   ├── device-api-local.yaml         SUT-B, plateforme en mode LOCAL
│   └── device-api-distributed.yaml   SUT-B, plateforme en mode DISTRIBUTED
└── README.md

platform-app/src/main/resources/sql/
└── seed-sut-devices.sql               copie classpath du script SUT V2 (INC-4 option A)
                                       — accessible via scriptPath: "classpath:sql/seed-sut-devices.sql"
```

---

## `docker-compose-sut.yaml`

```yaml
# =============================================================================
# SUT (System Under Test) — IoT Use Cases
# =============================================================================
# Lancé SÉPARÉMENT de la plateforme de performance.
#
# Usage :
#   docker compose -f platform-deployment/examples/docker-compose-sut.yaml up -d
#   docker compose -f platform-deployment/examples/docker-compose-sut.yaml down
#
# Services : iot-dispatcher, device-api, kafka-sut, postgres-sut, wiremock
# Ports exposés : 9093 (kafka), 5433 (postgres), 8084 (device-api),
#                 8083 (iot-dispatcher), 8090 (wiremock)
# Note: device-api expose 8084 (et NON 8082) pour éviter le conflit avec
#       agent-2 de la plateforme (docker-compose.yaml mappe host 8082→agent-2).
# =============================================================================

services:

  postgres-sut:
    image: postgres:16-alpine
    container_name: sut-postgres
    environment:
      POSTGRES_DB: sut_devices
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: changeme
    ports:
      - "5433:5432"
    volumes:
      - sut_postgres_data:/var/lib/postgresql/data
      - ../../platform-examples/sut-db/sql:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d sut_devices"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - sut-net

  kafka-sut:
    image: confluentinc/cp-kafka:7.7.1
    container_name: sut-kafka
    environment:
      CLUSTER_ID: "sut-kafka-dev"
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_NODE_ID: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka-sut:29093"
      KAFKA_LISTENERS: "PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9093,CONTROLLER://0.0.0.0:29093"
      KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka-sut:29092,PLAINTEXT_HOST://localhost:9093"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    ports:
      - "9093:9093"
    healthcheck:
      test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server localhost:29092 > /dev/null 2>&1"]
      interval: 10s
      timeout: 10s
      retries: 10
      start_period: 30s
    networks:
      - sut-net

  wiremock:
    image: wiremock/wiremock:3.12.1
    container_name: sut-wiremock
    ports:
      - "8090:8080"
    volumes:
      - ./wiremock:/home/wiremock
    command: --port 8080 --verbose --global-response-templating
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/__admin/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - sut-net

  iot-dispatcher:
    build:
      context: ../../platform-examples/iot-dispatcher
      dockerfile: Dockerfile
    container_name: sut-iot-dispatcher
    environment:
      KAFKA_BOOTSTRAP_SERVERS: "kafka-sut:29092"
      DB_URL: "jdbc:postgresql://postgres-sut:5432/sut_devices"
      DB_USERNAME: postgres
      DB_PASSWORD: changeme
      IOT_DEVICE_GATEWAY_URL: "http://wiremock:8080"
      IOT_TOPIC_COMMANDS: iot-commands
    ports:
      - "8083:8080"
    depends_on:
      kafka-sut:
        condition: service_healthy
      postgres-sut:
        condition: service_healthy
      wiremock:
        condition: service_healthy
    networks:
      - sut-net

  device-api:
    build:
      context: ../../platform-examples/device-api
      dockerfile: Dockerfile
    container_name: sut-device-api
    environment:
      KAFKA_BOOTSTRAP_SERVERS: "kafka-sut:29092"
      DB_URL: "jdbc:postgresql://postgres-sut:5432/sut_devices"
      DB_USERNAME: postgres
      DB_PASSWORD: changeme
      DEVICE_TOPIC_EVENTS: device-events
      SERVER_PORT: "8084"
    ports:
      - "8084:8084"
    depends_on:
      kafka-sut:
        condition: service_healthy
      postgres-sut:
        condition: service_healthy
    networks:
      - sut-net

networks:
  sut-net:
    driver: bridge

volumes:
  sut_postgres_data:
```

---

## Scénarios DSL

### `scenarios/iot-dispatcher-local.yaml`

```yaml
# =============================================================================
# Scénario : IoT Dispatcher — Mode LOCAL (plateforme)
# SUT-A : Kafka → DB lookup → HTTP dispatch vers IoT devices
#
# Prérequis :
#   - docker compose -f examples/docker-compose-sut.yaml up -d
#   - java -jar platform-app.jar (profil local, application-local.yaml ci-dessous)
#
# application-local.yaml (configuration technique — ne pas modifier ce scénario)
#   platform:
#     kafka-clusters:
#       iot-sut:
#         bootstrap-servers: localhost:9093
#         topics:
#           iot-commands: iot-commands
#     http-targets:
#       wiremock:
#         base-url: http://localhost:8090
#         paths:
#           reset-requests: /__admin/requests
#           count-requests: /__admin/requests/count
#       iot-dispatcher-health:
#         base-url: http://localhost:8083
# =============================================================================
name: iot-dispatcher-local
description: "Test de performance SUT-A (IoT Dispatcher) — mode LOCAL, 1 000 commandes"
version: "1.0"

slo:
  max-p99-ms: 500
  min-success-rate: 0.99

steps:

  - id: reset-wiremock
    name: Remettre WireMock à zéro
    type: preparation
    task: http-client
    parameters:
      target: wiremock
      method: DELETE
      path: reset-requests           # → /__admin/requests via paths map
      expectedStatus: 200

  - id: preload-iot-commands
    name: Précharger 1 000 commandes IoT dans Kafka
    type: preparation
    task: kafka-producer
    parameters:
      cluster: iot-sut
      topic: iot-commands            # → résolu depuis platform.kafka-clusters.iot-sut.topics.iot-commands
      messageCount: 1000
      messageTemplate: '{"device_id":"device-{index}","command":"ping","ts":"{timestamp}"}'
    dependsOn: [reset-wiremock]

  - id: wait-for-dispatch
    name: Attendre que le dispatcher traite les commandes (30s)
    type: preparation
    task: http-client
    parameters:
      target: iot-dispatcher-health
      method: GET
      path: /actuator/health
      expectedStatus: 200
    timeout: 35s
    dependsOn: [preload-iot-commands]

  - id: inject-load
    name: Injecter charge Kafka simultanée (1 000 messages)
    type: injection
    task: kafka-producer            # le SUT iot-dispatcher consomme du Kafka — l'injection produit du Kafka, pas du HTTP (Gatling ne produit pas Kafka)
    parameters:
      cluster: iot-sut             # → platform.kafka-clusters.iot-sut (aucune URL inline — ADR-015/ADR-016)
      topic: iot-commands          # → résolu depuis platform.kafka-clusters.iot-sut.topics.iot-commands
      messageCount: 1000
      messageTemplate: '{"device_id":"device-{index}","command":"load-test"}'
    dependsOn: [wait-for-dispatch]

  - id: assert-kafka-lag
    name: Vérifier que le lag Kafka est 0 (tout traité)
    type: assertion
    task: kafka
    parameters:
      cluster: iot-sut
      topic: iot-commands
      expectedLag: 0
    dependsOn: [inject-load]

  - id: assert-http-dispatched
    name: Vérifier que WireMock a reçu les commandes HTTP
    type: assertion
    task: http-mock
    parameters:
      target: wiremock
      urlPattern: "/command"
      expectedRequestCount: 1000
      operator: GREATER_THAN_OR_EQUAL
    dependsOn: [inject-load]
```

### `scenarios/device-api-local.yaml`

```yaml
# =============================================================================
# Scénario : Device API — Mode LOCAL (plateforme)
# SUT-B : POST HTTP → DB lookup → Kafka produce
#
# Prérequis :
#   - docker compose -f examples/docker-compose-sut.yaml up -d
#   - java -jar platform-app.jar (profil local)
#
# application-local.yaml (conf technique)
#   platform:
#     datasources:
#       sut-db:
#         url: jdbc:postgresql://localhost:5433/sut_devices
#         username: postgres
#         password: changeme
#     kafka-clusters:
#       iot-sut:
#         bootstrap-servers: localhost:9093
#         topics:
#           device-events: device-events
#     http-targets:
#       device-api:
#         base-url: http://localhost:8084
#         paths:
#           submit-event: /api/events
# =============================================================================
name: device-api-local
description: "Test de performance SUT-B (Device API) — mode LOCAL, 10 000 req/s cible"
version: "1.0"

slo:
  max-p99-ms: 200
  min-success-rate: 0.999

steps:

  - id: reset-devices
    name: Vider la table devices avant re-seed (état propre)
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: PURGE
      table: devices                # seule la table devices existe (PDR-023) — pas de table events
    # Note: PURGE avant POPULATE car le script de seed contient ON CONFLICT DO NOTHING

  - id: seed-devices
    name: Insérer 10 000 devices en base
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: POPULATE
      scriptPath: "classpath:sql/seed-sut-devices.sql"   # copie classpath du script SUT V2 (INC-4 option A)
    dependsOn: [reset-devices]

  - id: warmup
    name: Warmup JVM — 100 requêtes
    type: preparation
    task: http-client
    parameters:
      target: device-api
      method: POST
      path: submit-event             # → /api/events via paths map
      body: '{"device_id":"device-0001"}'
      expectedStatus: 200
    dependsOn: [seed-devices]

  - id: load-test
    name: Charge 1 000 users concurrents, ramp 60s
    type: injection
    task: gatling
    loadModel:
      type: RAMP_UP
      target: 1000
      duration: 60s
    gatling:
      target: device-api            # → platform.http-targets.device-api.base-url (aucune URL inline — ADR-015/ADR-016)
      simulations:
        - name: DeviceApiSimulation
          path: submit-event        # → /api/events résolu depuis platform.http-targets.device-api.paths
          method: POST
          bodyTemplate: '{"device_id":"device-{index}"}'
          contentType: application/json
    dependsOn: [warmup]

  - id: assert-kafka-produced
    name: Vérifier que Kafka a reçu les événements
    type: assertion
    task: kafka
    parameters:
      cluster: iot-sut
      topic: device-events           # → résolu depuis topics map
      expectedMinCount: 50000
      operator: GREATER_THAN_OR_EQUAL
    dependsOn: [load-test]

  - id: assert-db-unchanged
    name: Vérifier que les devices ne sont pas altérés
    type: assertion
    task: database
    parameters:
      datasource: sut-db
      query: "SELECT COUNT(*) FROM devices WHERE is_active = true"
      expectedValue: 10000
      operator: EQUAL
    dependsOn: [load-test]
```

> **Note** : les scénarios `*-distributed.yaml` ont la même structure mais avec des `AGENT_TAGS` pour distribuer les steps sur des agents spécialisés (ex: agent `kafka` pour les steps kafka-producer, agent `gatling` pour l'injection).

---

## WireMock Stub (`wiremock/mappings/iot-command.json`)

```json
{
  "mappings": [
    {
      "name": "IoT Command Endpoint",
      "request": {
        "method": "POST",
        "urlPattern": "/command"
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "body": "{\"status\":\"accepted\",\"ts\":\"{{now}}\"}"
      }
    },
    {
      "name": "IoT Health",
      "request": { "method": "GET", "url": "/health" },
      "response": { "status": 200, "body": "{\"status\":\"UP\"}" }
    }
  ]
}
```

---

## README (structure minimale)

```markdown
# Exemples IoT — Performance Engineering Platform

## Prérequis
- Docker + Docker Compose
- Java 25 + Maven (pour la plateforme)

## Démarrage rapide (5 étapes)

### 1. Démarrer les services SUT (System Under Test)
docker compose -f platform-deployment/examples/docker-compose-sut.yaml up -d

### 2a. Mode LOCAL (un seul process)
java -jar platform-app/target/performance-platform.jar \
  --spring.profiles.active=local,examples-local

### 2b. Mode DISTRIBUTED (orchestrateur + agents)
docker compose -f platform-deployment/docker/docker-compose.yaml up -d

### 3. Soumettre un scénario
# SUT-A : IoT Dispatcher (LOCAL)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/iot-dispatcher-local.yaml

# SUT-B : Device API (LOCAL)
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/device-api-local.yaml

### 4. Suivre l'exécution
curl http://localhost:8080/executions/{id}/status

### 5. Arrêter le SUT
docker compose -f platform-deployment/examples/docker-compose-sut.yaml down
```

---

## Règles de Comportement

- **`docker-compose-sut.yaml` est 100% autonome** : peut démarrer sans la plateforme.
- **Ports non-conflictuels** : SUT utilise 9093 (kafka), 5433 (postgres), 8083 (iot-dispatcher), 8084 (device-api), 8090 (wiremock) — différents des ports plateforme exposés sur l'hôte (9092 kafka, 5432 postgres, 8080 orchestrateur, 8081 agent-1, 8082 agent-2, 8090 wiremock plateforme). Note : device-api est sur 8084 (et non 8082) car la plateforme mappe déjà host 8082→agent-2 dans `platform-deployment/docker/docker-compose.yaml`.
- **Les scénarios `*-local.yaml` et `*-distributed.yaml` sont identiques** sauf pour l'attribution des steps aux agents (`agentTags:` ajouté dans la version distributed).
- **Aucune URL technique inline dans les scénarios** : uniquement des références logiques. Les URLs réelles sont dans `application-local.yaml` ou `application-orchestrator.yaml`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-020 (KafkaClusterRegistry)   → scénarios: cluster: iot-sut, topic: iot-commands
  PDR-022 (HttpTargetRegistry)     → scénarios: target: wiremock, path: reset-requests
  PDR-023 (SUT services)           → docker-compose-sut référence les images buildées
  platform-deployment/docker/      → docker-compose.yaml existant pour la plateforme

Ce PDR est utilisé par :
  Rien — terminal dans le graphe de dépendances
```

---

## Critères de Done (PDR complet)

- [ ] ISSUE-099, 100, 101, 102 toutes DONE
- [ ] `docker-compose-sut.yaml` : `docker compose config` sans erreur YAML
- [ ] `scenarios/iot-dispatcher-local.yaml` : parseable par ScenarioParser (syntaxe DSL valide)
- [ ] `scenarios/device-api-local.yaml` : parseable par ScenarioParser (syntaxe DSL valide)
- [ ] WireMock stub JSON valide
- [ ] README : guide step-by-step testé (5 étapes claires)
