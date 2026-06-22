# Exemples IoT — Performance Engineering Platform

Deux use cases réels de test de performance sur des services IoT :
- **SUT-A — IoT Dispatcher** : consomme Kafka → vérifie DB → dispatch HTTP vers 10k IoT devices
- **SUT-B — Device API** : reçoit HTTP → vérifie DB → publie dans Kafka

---

## Architecture

```
┌─────────────────────┐        ┌──────────────────────────────────┐
│  SUT (docker-compose│        │  Plateforme Performance           │
│       -sut.yaml)    │        │  (docker-compose.yaml ou JVM)    │
│                     │        │                                   │
│  iot-dispatcher:8083│◄───────│  kafka-producer (préparation)     │
│  device-api    :8084│◄───────│  gatling        (injection)       │
│  kafka-sut     :9093│◄──────►│  kafka assertion, http-mock       │
│  postgres-sut  :5433│◄──────►│  database assertion               │
│  wiremock      :8090│        │                                   │
└─────────────────────┘        └──────────────────────────────────┘
```

## Prérequis

- Docker + Docker Compose v2
- Java 25 + Maven 3.9+ (pour la plateforme)
- 4 Go RAM disponibles (SUT + plateforme)

---

## Démarrage Rapide

### Étape 1 — Démarrer le SUT

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
| wiremock | 8090 | Simule les 10k endpoints IoT (POST /command) |

### Étape 2a — Mode LOCAL (un seul process)

```bash
# Builder la plateforme
mvn clean package -DskipTests -q

# Démarrer en mode LOCAL avec le profil d'exemples
java -jar platform-app/target/performance-platform.jar \
  --spring.profiles.active=local,examples-local
```

> La plateforme se connecte au SUT via `localhost:9093`, `localhost:5433`, etc.

### Étape 2b — Mode DISTRIBUTED (orchestrateur + agents)

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

---

## Lancer les Scénarios

### SUT-A : IoT Dispatcher

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

### SUT-B : Device API

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

### Suivre l'exécution

```bash
# Status de l'exécution
curl http://localhost:8080/executions/{execution-id}/status

# Logs plateforme (mode LOCAL)
# → stdout de la JVM

# Logs plateforme (mode DISTRIBUTED)
docker compose -f platform-deployment/docker/docker-compose.yaml logs orchestrator -f
```

---

## Adapter pour votre Environnement

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

## Arrêter le SUT

```bash
docker compose -f platform-deployment/examples/docker-compose-sut.yaml down

# Avec suppression des données (reset complet)
docker compose -f platform-deployment/examples/docker-compose-sut.yaml down -v
```
