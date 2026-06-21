# ISSUE-100 — Scénarios YAML iot-dispatcher (LOCAL + DISTRIBUTED)

**PDR** : PDR-024
**Module** : `platform-deployment/examples/scenarios/`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-086 (cluster: ref), ISSUE-092 (target: ref), ISSUE-099 (SUT docker-compose)
**Estime** : M

---

## Objectif

Créer les deux scénarios YAML pour le use case SUT-A (IoT Dispatcher) :
- `iot-dispatcher-local.yaml` : plateforme en mode LOCAL (1 JVM, InMemory transport, pas d'agents)
- `iot-dispatcher-distributed.yaml` : plateforme en mode DISTRIBUTED (orchestrateur + 2 agents Kafka)

Les scénarios doivent être **parsables par `ScenarioParser`** (syntaxe DSL valide) et démontrer :
1. La résolution de topics logiques (`topic: iot-commands` → nom réel depuis `application-*.yaml`)
2. La résolution de targets HTTP logiques (`target: wiremock` → URL depuis `application-*.yaml`)
3. L'enchaînement preparation → injection → assertion
4. Les dépendances entre steps (`dependsOn:`)

---

## Fichiers à Créer

```
platform-deployment/examples/scenarios/
  ├── iot-dispatcher-local.yaml
  └── iot-dispatcher-distributed.yaml

platform-app/src/main/resources/
  └── application-examples-local.yaml    — profil Spring pour les exemples en mode LOCAL
```

---

## Contenu des Fichiers

### `iot-dispatcher-local.yaml`
Voir PDR-024 section "scenarios/iot-dispatcher-local.yaml" pour le contenu complet.

Steps obligatoires :
1. `reset-wiremock` (http-client, DELETE, `target: wiremock`, `path: reset-requests`)
2. `preload-iot-commands` (kafka-producer, `cluster: iot-sut`, `topic: iot-commands`, 1 000 messages)
3. `wait-for-processing` (http-client, GET health check sur iot-dispatcher)
4. `inject-load` (kafka-producer injection — le SUT iot-dispatcher consomme du Kafka ; Gatling ne produit pas Kafka, donc l'injection se fait via kafka-producer avec `cluster: iot-sut` + `topic: iot-commands`, aucune URL inline)
5. `assert-kafka-lag` (kafka assertion, `cluster: iot-sut`, `expectedLag: 0`)
6. `assert-http-dispatched` (http-mock assertion, `target: wiremock`, `expectedRequestCount: 1000`)

### `iot-dispatcher-distributed.yaml`

Même structure que le local, avec deux différences :
- Ajout de `agentTags` sur les steps Kafka pour les distribuer sur un agent spécialisé
- `messageCount: 10000` (charge plus importante en distributed)

```yaml
name: iot-dispatcher-distributed
description: "Test SUT-A IoT Dispatcher — mode DISTRIBUTED, 10 000 commandes"
version: "1.0"

slo:
  max-p99-ms: 1000
  min-success-rate: 0.99

steps:

  - id: reset-wiremock
    name: Remettre WireMock à zéro
    type: preparation
    task: http-client
    parameters:
      target: wiremock
      method: DELETE
      path: reset-requests
      expectedStatus: 200

  - id: preload-iot-commands
    name: Précharger 10 000 commandes IoT
    type: preparation
    task: kafka-producer
    agentTags: [kafka, standard]        # agent spécialisé kafka
    parameters:
      cluster: iot-sut
      topic: iot-commands
      messageCount: 10000
      messageTemplate: '{"device_id":"device-{index}","command":"ping","ts":"{timestamp}"}'
    dependsOn: [reset-wiremock]

  - id: inject-load
    name: Injecter charge simultanée (500 users, 60s)
    type: injection
    task: gatling
    agentTags: [gatling, high-memory]   # agent avec plus de RAM pour Gatling
    loadModel:
      type: RAMP_UP
      target: 500
      duration: 60s
    gatling:
      target: iot-dispatcher-health   # → platform.http-targets.iot-dispatcher-health.base-url (aucune URL inline — ADR-015/ADR-016)
      simulations:
        - name: IotDispatcherMonitorSimulation
          path: health                # → /actuator/health résolu depuis paths map
          method: GET
    dependsOn: [preload-iot-commands]

  - id: assert-kafka-lag
    name: Vérifier lag Kafka = 0
    type: assertion
    task: kafka
    parameters:
      cluster: iot-sut
      topic: iot-commands
      expectedLag: 0
    dependsOn: [inject-load]

  - id: assert-http-dispatched
    name: Vérifier que WireMock a reçu 10 000 commandes HTTP
    type: assertion
    task: http-mock
    parameters:
      target: wiremock
      urlPattern: "/command"
      expectedRequestCount: 10000
      operator: GREATER_THAN_OR_EQUAL
    dependsOn: [inject-load]
```

### `application-examples-local.yaml`

```yaml
# Profil Spring pour les exemples IoT en mode LOCAL
# Utilisé avec : --spring.profiles.active=local,examples-local
# Prérequis : docker-compose-sut.yaml en cours d'exécution

platform:
  kafka-clusters:
    iot-sut:
      bootstrap-servers: ${IOT_KAFKA_SERVERS:localhost:9093}
      producer-acks: "1"
      consumer-group: perf-test-consumer
      topics:
        iot-commands: ${IOT_TOPIC_COMMANDS:iot-commands}
        device-events: ${DEVICE_TOPIC_EVENTS:device-events}

  http-targets:
    wiremock:
      base-url: ${WIREMOCK_URL:http://localhost:8090}
      connection-timeout: 2s
      read-timeout: 5s
      paths:
        reset-requests: /__admin/requests
        count-requests: /__admin/requests/count
    iot-dispatcher-health:
      base-url: ${IOT_DISPATCHER_URL:http://localhost:8083}
      read-timeout: 10s
      paths:
        health: /actuator/health
```

---

## Règles Spécifiques

- **Syntaxe DSL** : les fichiers doivent être parsables par `ScenarioParser` sans erreur. Le Developer doit vérifier en chargeant le parser (test unitaire ou test d'intégration).
- **`dependsOn:`** : tous les steps doivent avoir des dépendances explicites pour garantir l'ordre d'exécution.
- **Aucune URL technique dans les scénarios** : uniquement `target:`, `cluster:`, `topic:` avec noms logiques.
- **Commentaires YAML** : les scénarios doivent avoir des commentaires expliquant le prérequis (`docker-compose-sut.yaml`) et la config Spring nécessaire.

---

## Critères de Done

- [ ] `iot-dispatcher-local.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `iot-dispatcher-distributed.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `application-examples-local.yaml` : binding Spring valide (`@ConfigurationProperties`)
- [ ] Aucune URL inline dans les scénarios (uniquement `target:`, `cluster:`, `topic:`) — y compris dans les blocs `gatling:` (pas de `baseUrl:`, utiliser `target:`)
- [ ] L'injection vers iot-dispatcher passe par `kafka-producer` (`cluster: iot-sut`) — Gatling n'est utilisé que pour le monitoring HTTP via `target: iot-dispatcher-health`
- [ ] Steps avec `dependsOn:` explicites dans les deux scénarios
- [ ] `agentTags:` présents dans `iot-dispatcher-distributed.yaml`
- [ ] `.claude/progress.md` mis à jour : ISSUE-100 → DONE
