# ISSUE-101 — Scénarios YAML device-api (LOCAL + DISTRIBUTED)

**PDR** : PDR-024
**Module** : `platform-deployment/examples/scenarios/`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-086 (cluster: ref), ISSUE-092 (target: ref), ISSUE-099 (SUT docker-compose)
**Estime** : M

---

## Objectif

Créer les deux scénarios YAML pour le use case SUT-B (Device API) :
- `device-api-local.yaml` : plateforme en mode LOCAL
- `device-api-distributed.yaml` : plateforme en mode DISTRIBUTED

Ces scénarios démontrent le pattern classique de test de charge :
`DB preparation` → `Gatling HTTP load` → `Kafka assertion` → `DB assertion`

---

## Fichiers à Créer

```
platform-deployment/examples/scenarios/
  ├── device-api-local.yaml
  └── device-api-distributed.yaml

platform-app/src/main/resources/
  └── application-examples-local.yaml    — déjà créé dans ISSUE-100, ajouter le bloc device-api
```

---

## Contenu des Fichiers

### `device-api-local.yaml`

```yaml
# =============================================================================
# Scénario : Device API — Mode LOCAL (plateforme)
# SUT-B : POST HTTP → DB lookup → Kafka produce
#
# Prérequis :
#   docker compose -f platform-deployment/examples/docker-compose-sut.yaml up -d
#   java -jar platform-app.jar --spring.profiles.active=local,examples-local
#
# Configuration technique dans application-examples-local.yaml :
#   platform.datasources.sut-db    → jdbc:postgresql://localhost:5433/sut_devices
#   platform.kafka-clusters.iot-sut → localhost:9093 / topic: device-events
#   platform.http-targets.device-api → http://localhost:8082
# =============================================================================
name: device-api-local
description: "Test SUT-B Device API — mode LOCAL, cible 1 000 req/s"
version: "1.0"

slo:
  max-p99-ms: 200
  min-success-rate: 0.999

steps:

  - id: purge-and-reseed
    name: Repeupler la table devices (état propre)
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: POPULATE
      scriptPath: "classpath:sql/V2__seed_10k_devices.sql"

  - id: warmup
    name: Warmup JVM device-api (100 requêtes)
    type: preparation
    task: http-client
    parameters:
      target: device-api
      method: POST
      path: submit-event            # → /api/events via paths map
      body: '{"device_id":"device-0001"}'
      expectedStatus: 200
    timeout: 10s
    dependsOn: [purge-and-reseed]

  - id: check-health
    name: Vérifier que device-api est prêt
    type: preparation
    task: http-client
    parameters:
      target: device-api
      method: GET
      path: /api/health
      expectedStatus: 200
    dependsOn: [warmup]

  - id: load-test
    name: Charge 1 000 users concurrents — ramp 60s
    type: injection
    task: gatling
    loadModel:
      type: RAMP_UP
      target: 1000
      duration: 60s
    gatling:
      baseUrl: "http://localhost:8082"
      simulations:
        - name: DeviceApiSimulation
          path: /api/events
          method: POST
          queryParams:
            device_id: "device-{index}"
          contentType: application/json
    dependsOn: [check-health]

  - id: assert-kafka-produced
    name: Vérifier que Kafka a reçu les événements
    type: assertion
    task: kafka
    parameters:
      cluster: iot-sut
      topic: device-events          # → résolu depuis topics map
      expectedMinCount: 50000
      operator: GREATER_THAN_OR_EQUAL
    dependsOn: [load-test]

  - id: assert-db-intact
    name: Vérifier que la table devices n'a pas été altérée
    type: assertion
    task: database
    parameters:
      datasource: sut-db
      query: "SELECT COUNT(*) FROM devices WHERE is_active = true"
      expectedValue: 10000
      operator: EQUAL
    dependsOn: [load-test]
```

### `device-api-distributed.yaml`

```yaml
# Mode DISTRIBUTED — mêmes steps, charge plus élevée, steps distribués sur agents
name: device-api-distributed
description: "Test SUT-B Device API — mode DISTRIBUTED, cible 10 000 req/s"
version: "1.0"

slo:
  max-p99-ms: 500
  min-success-rate: 0.999

steps:

  - id: purge-and-reseed
    name: Repeupler la table devices
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: POPULATE
      scriptPath: "classpath:sql/V2__seed_10k_devices.sql"

  - id: check-health
    name: Vérifier que device-api est prêt
    type: preparation
    task: http-client
    parameters:
      target: device-api
      method: GET
      path: /api/health
      expectedStatus: 200
    dependsOn: [purge-and-reseed]

  - id: load-test
    name: Charge 10 000 users — ramp 120s
    type: injection
    task: gatling
    agentTags: [gatling, high-memory]   # agent spécialisé Gatling avec RAM suffisante
    loadModel:
      type: RAMP_UP
      target: 10000
      duration: 120s
    gatling:
      baseUrl: "http://device-api:8082"  # réseau Docker interne
      simulations:
        - name: DeviceApiHighLoadSimulation
          path: /api/events
          method: POST
          queryParams:
            device_id: "device-{index}"
    dependsOn: [check-health]

  - id: assert-kafka-produced
    name: Vérifier les événements Kafka
    type: assertion
    task: kafka
    agentTags: [kafka, standard]
    parameters:
      cluster: iot-sut
      topic: device-events
      expectedMinCount: 500000
      operator: GREATER_THAN_OR_EQUAL
    dependsOn: [load-test]

  - id: assert-db-intact
    name: Vérifier intégrité base de données
    type: assertion
    task: database
    agentTags: [standard]
    parameters:
      datasource: sut-db
      query: "SELECT COUNT(*) FROM devices WHERE is_active = true"
      expectedValue: 10000
      operator: EQUAL
    dependsOn: [load-test]
```

### Ajout à `application-examples-local.yaml` (depuis ISSUE-100)

```yaml
# Ajouter dans platform.datasources et platform.http-targets
platform:
  datasources:
    sut-db:
      url: ${SUT_DB_URL:jdbc:postgresql://localhost:5433/sut_devices}
      username: ${SUT_DB_USERNAME:postgres}
      password: ${SUT_DB_PASSWORD:changeme}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2

  http-targets:
    # (existant depuis ISSUE-100 : wiremock, iot-dispatcher-health)
    device-api:
      base-url: ${DEVICE_API_URL:http://localhost:8082}
      connection-timeout: 2s
      read-timeout: 10s
      paths:
        submit-event: /api/events
        health: /api/health
```

---

## Règles Spécifiques

- Mêmes règles que ISSUE-100 : syntaxe DSL valide, aucune URL inline, `dependsOn:` explicites.
- L'assertion `task: database` avec `query:` et `expectedValue:` doit correspondre à la signature du `DatabaseAssertionExecutor` existant (ISSUE-061). Le Developer vérifie la syntaxe DSL contre l'implémentation existante.
- `scriptPath: "classpath:sql/..."` → le script SQL de seed doit être accessible dans le classpath de la plateforme. Vérifier que la convention `classpath:` est supportée par `DatabaseTaskExecutor`.

---

## Critères de Done

- [ ] `device-api-local.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `device-api-distributed.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `application-examples-local.yaml` : section `datasources.sut-db` + `http-targets.device-api` ajoutée
- [ ] Aucune URL inline dans les scénarios
- [ ] Steps `dependsOn:` corrects (order : reseed → health → load → assertions)
- [ ] `agentTags:` dans les steps du scénario distributed
- [ ] `.claude/progress.md` mis à jour : ISSUE-101 → DONE
