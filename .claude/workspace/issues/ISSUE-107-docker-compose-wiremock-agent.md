# ISSUE-107 — Create docker-compose-wiremock-agent.yaml

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : DONE
**Priorite** : P1 (critique — necessaire pour le demo DISTRIBUTED mock-as-agent)
**Bloquee par** : ISSUE-103 (le docker-compose-sut ne contient plus de wiremock standalone — modele coherent)
**Estime** : M (1-3h)

---

## Objectif

Creer un fichier `docker-compose-wiremock-agent.yaml` qui demontre le pattern "WireMock as Agent" avec 3 agents specialises via le modele configuration-driven. Ce fichier est l'infrastructure pour executer le scenario `http-api-mock-agent-distributed.yaml`.

Architecture :
- **postgres** : base de donnees de la plateforme (etat des executions)
- **kafka** : transport entre orchestrateur et agents
- **orchestrator** : recoit le scenario, planifie le DAG, dispatche en broadcast
- **mock-agent** : `agent.supported-tasks: [mock-server, http-client]` — demarre WireMock EMBEDDED en preparation
- **gatling-agent** : `agent.supported-tasks: [gatling, gatling-metric]` — execute l'injection de charge
- **standard-agent** : `agent.supported-tasks: [http-mock, database, kafka]` — execute les assertions

**PAS de service WireMock standalone.** Le mock est fourni par l'agent.

**Configuration-driven model** : Chaque agent recoit sa liste de tasks via la variable d'environnement `AGENT_SUPPORTED_TASKS` (mappe a `agent.supported-tasks` dans application.yml). Meme image Docker partout — config differente = role different. Pas de `AGENT_TAGS` (les tags ne sont plus utilises pour le routing).

## Fichier a Creer

```
platform-deployment/examples/
  └── docker-compose-wiremock-agent.yaml
```

## docker-compose-wiremock-agent.yaml — Contenu

```yaml
# =============================================================================
# Performance Engineering Platform — WireMock as Agent Demo
# =============================================================================
# Usage:
#   docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml build
#   docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml up -d
#   docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml down
#
# Services: orchestrator + mock-agent + gatling-agent + standard-agent + kafka + postgres
#
# Architecture :
#   - PAS de service WireMock standalone.
#   - mock-agent demarre WireMock EMBEDDED en reponse a un step mock-server.
#   - Gatling et assertions communiquent avec le mock via son URL (publiee dans ExecutionContext).
#   - Configuration-driven model : chaque agent recoit AGENT_SUPPORTED_TASKS env var.
#     Meme image Docker pour tous, config differente = role different.
#   - Routing par task name uniquement (broadcast + TaskSpecializationFilter cote agent).
#     Pas de AGENT_TAGS (les tags ne sont pas utilises pour le routing).
#
# Mode/role/transport via env vars (ADR-006 priority).
# Transport KAFKA only. No gRPC.
# =============================================================================

services:

  # ---- PostgreSQL 16 (Alpine) ----
  postgres:
    image: postgres:16-alpine
    container_name: perf-postgres
    environment:
      POSTGRES_DB: performance
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: changeme
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ../docker/init-scripts:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d performance"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - perf-net

  # ---- Kafka (KRaft — single-node, no Zookeeper) ----
  kafka:
    image: confluentinc/cp-kafka:7.7.1
    container_name: perf-kafka
    environment:
      CLUSTER_ID: "perf-mock-agent-demo"
      KAFKA_PROCESS_ROLES: "broker,controller"
      KAFKA_NODE_ID: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka:29093"
      KAFKA_LISTENERS: "PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093"
      KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
    healthcheck:
      test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server localhost:29092 > /dev/null 2>&1 || exit 1"]
      interval: 10s
      timeout: 10s
      retries: 10
      start_period: 30s
    networks:
      - perf-net

  # ---- Orchestrator (MODE=ORCHESTRATOR) ----
  orchestrator:
    build:
      context: ../../
      dockerfile: platform-deployment/docker/Dockerfile
    container_name: perf-orchestrator
    environment:
      RUNTIME_MODE: DISTRIBUTED
      MODE: ORCHESTRATOR
      TRANSPORT_TYPE: KAFKA
      SPRING_PROFILES_ACTIVE: orchestrator
      KAFKA_BOOTSTRAP_SERVERS: "kafka:29092"
      DB_URL: "jdbc:postgresql://postgres:5432/performance"
      DB_USERNAME: "postgres"
      DB_PASSWORD: "changeme"
      PLATFORM_SECURITY_ENABLED: "false"
    ports:
      - "8080:8080"
    volumes:
      - ./scenarios:/scenarios:ro
      - ./reports:/reports
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - perf-net

  # ---- Mock Agent ----
  # Configuration-driven specialization via AGENT_SUPPORTED_TASKS.
  # Demarre WireMock EMBEDDED en reponse a un step mock-server.
  # Accessible depuis les autres agents via le reseau Docker interne.
  mock-agent:
    build:
      context: ../../
      dockerfile: platform-deployment/docker/Dockerfile
    container_name: perf-mock-agent
    environment:
      RUNTIME_MODE: DISTRIBUTED
      MODE: AGENT
      TRANSPORT_TYPE: KAFKA
      SPRING_PROFILES_ACTIVE: agent
      KAFKA_BOOTSTRAP_SERVERS: "kafka:29092"
      AGENT_ID: "mock-agent"
      AGENT_SUPPORTED_TASKS: "mock-server,http-client"
      PLATFORM_SECURITY_ENABLED: "false"
    expose:
      - "8080"
    depends_on:
      kafka:
        condition: service_healthy
      orchestrator:
        condition: service_started
    networks:
      - perf-net

  # ---- Gatling Agent ----
  # Configuration-driven specialization via AGENT_SUPPORTED_TASKS.
  # Agent avec ressources pour les simulations Gatling.
  gatling-agent:
    build:
      context: ../../
      dockerfile: platform-deployment/docker/Dockerfile
    container_name: perf-gatling-agent
    environment:
      RUNTIME_MODE: DISTRIBUTED
      MODE: AGENT
      TRANSPORT_TYPE: KAFKA
      SPRING_PROFILES_ACTIVE: agent
      KAFKA_BOOTSTRAP_SERVERS: "kafka:29092"
      AGENT_ID: "gatling-agent"
      AGENT_SUPPORTED_TASKS: "gatling,gatling-metric"
      PLATFORM_SECURITY_ENABLED: "false"
    ports:
      - "8081:8080"
    depends_on:
      kafka:
        condition: service_healthy
      orchestrator:
        condition: service_started
    networks:
      - perf-net

  # ---- Standard Agent ----
  # Configuration-driven specialization via AGENT_SUPPORTED_TASKS.
  # Agent pour les assertions et operations standard.
  standard-agent:
    build:
      context: ../../
      dockerfile: platform-deployment/docker/Dockerfile
    container_name: perf-standard-agent
    environment:
      RUNTIME_MODE: DISTRIBUTED
      MODE: AGENT
      TRANSPORT_TYPE: KAFKA
      SPRING_PROFILES_ACTIVE: agent
      KAFKA_BOOTSTRAP_SERVERS: "kafka:29092"
      AGENT_ID: "standard-agent"
      AGENT_SUPPORTED_TASKS: "http-mock,database,kafka"
      PLATFORM_SECURITY_ENABLED: "false"
    ports:
      - "8082:8080"
    depends_on:
      kafka:
        condition: service_healthy
      orchestrator:
        condition: service_started
    networks:
      - perf-net

# ---- Infrastructure ----
networks:
  perf-net:
    driver: bridge

volumes:
  postgres_data:
```

## Regles Specifiques

- **PAS de service WireMock standalone** — c'est le point central de ce PDR
- **Configuration-driven model** : Chaque agent utilise `AGENT_SUPPORTED_TASKS` env var (mappe a `agent.supported-tasks` dans application.yml). Pas de `AGENT_TAGS` (les tags ne sont plus utilises pour le routing).
- **Meme image Docker** : Tous les agents partagent le meme Dockerfile (`platform-deployment/docker/Dockerfile`) — artefact unique (CF-01). Config differente = role different.
- `mock-agent` utilise `expose` (pas `ports`) pour eviter les collisions de ports. Le port WireMock EMBEDDED est attribue dynamiquement et accessible via le reseau Docker interne.
- Le repertoire `init-scripts/` est reference depuis `../docker/` (chemin relatif au fichier compose dans `examples/`)
- Les volumes `./scenarios:/scenarios:ro` et `./reports:/reports` permettent d'injecter les scenarios et de recuperer les rapports
- **`AGENT_SUPPORTED_TASKS` env var** : Liste de task names separee par des virgules. Mappe a la propriété Spring `agent.supported-tasks` (YAML list). Exemple : `AGENT_SUPPORTED_TASKS=mock-server,http-client` devient `agent.supported-tasks: [mock-server, http-client]`.
- Si le mecanisme de mapping `AGENT_SUPPORTED_TASKS` env var -> `agent.supported-tasks` n'existe pas encore dans le code, le Developer doit implementer ce mapping dans le cadre de cette Issue. Le mapping est trivial : `@ConfigurationProperties(prefix = "agent")` avec une `List<String> supportedTasks` et Spring Boot relie automatiquement la notation avec underscore (`AGENT_SUPPORTED_TASKS`) a la propriete camelCase (`agent.supported-tasks`).

## Criteres de Done

- [ ] `docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml config` -> YAML valide, 6 services
- [ ] Aucun service nomme `wiremock` dans le fichier
- [ ] Chaque agent a `AGENT_SUPPORTED_TASKS` env var avec sa liste de tasks
- [ ] Aucun `AGENT_TAGS` dans le fichier
- [ ] Le mapping `AGENT_SUPPORTED_TASKS` -> `agent.supported-tasks` fonctionne (ou est implemente si manquant)
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-107 -> DONE
