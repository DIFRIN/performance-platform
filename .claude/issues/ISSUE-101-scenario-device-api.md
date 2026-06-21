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

platform-app/src/main/resources/sql/
  └── seed-sut-devices.sql               — copie de platform-examples/sut-db/sql/V2__seed_10k_devices.sql (source PDR-023)
                                           (renommé sans préfixe Flyway), accessible via classpath:sql/seed-sut-devices.sql
                                           (INC-4 option A). INSERT ... ON CONFLICT DO NOTHING, 10 000 devices.
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
#   platform.http-targets.device-api → http://localhost:8084  (8084 et non 8082 — conflit agent-2 plateforme, INC-3)
# =============================================================================
name: device-api-local
description: "Test SUT-B Device API — mode LOCAL, cible 1 000 req/s"
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
      table: devices                # seule la table devices existe (PDR-023)
    # PURGE avant POPULATE car le script de seed contient ON CONFLICT DO NOTHING

  - id: seed-devices
    name: Insérer 10 000 devices en base (état propre)
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: POPULATE
      scriptPath: "classpath:sql/seed-sut-devices.sql"   # copie classpath du script SUT V2 (INC-4 option A)
    dependsOn: [reset-devices]

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
    dependsOn: [seed-devices]

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
      target: device-api            # → platform.http-targets.device-api.base-url (aucune URL inline — ADR-015/ADR-016)
      simulations:
        - name: DeviceApiSimulation
          path: submit-event        # → /api/events résolu depuis platform.http-targets.device-api.paths
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

  - id: reset-devices
    name: Vider la table devices avant re-seed
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: PURGE
      table: devices                # seule la table devices existe (PDR-023)
    # PURGE avant POPULATE car le script de seed contient ON CONFLICT DO NOTHING

  - id: seed-devices
    name: Repeupler la table devices
    type: preparation
    task: database
    parameters:
      datasource: sut-db
      operation: POPULATE
      scriptPath: "classpath:sql/seed-sut-devices.sql"   # copie classpath du script SUT V2 (INC-4 option A)
    dependsOn: [reset-devices]

  - id: check-health
    name: Vérifier que device-api est prêt
    type: preparation
    task: http-client
    parameters:
      target: device-api
      method: GET
      path: /api/health
      expectedStatus: 200
    dependsOn: [seed-devices]

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
      target: device-api            # → platform.http-targets.device-api.base-url (résolu depuis application-*.yaml selon l'env : localhost en LOCAL, host.docker.internal/réseau Docker en DISTRIBUTED — aucune URL inline, ADR-015/ADR-016)
      simulations:
        - name: DeviceApiHighLoadSimulation
          path: submit-event        # → /api/events résolu depuis platform.http-targets.device-api.paths
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
      base-url: ${DEVICE_API_URL:http://localhost:8084}   # 8084 et non 8082 — conflit agent-2 plateforme (INC-3)
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
- `scriptPath: "classpath:sql/seed-sut-devices.sql"` → le script SQL de seed est copié depuis `platform-examples/sut-db/sql/V2__seed_10k_devices.sql` vers `platform-app/src/main/resources/sql/seed-sut-devices.sql` (renommé sans préfixe Flyway pour ne pas déclencher Flyway sur la base plateforme). Vérifier que la convention `classpath:` est supportée par `DatabaseTaskExecutor`.
- Le step `reset-devices` (PURGE table `devices`) précède le step `seed-devices` (POPULATE) : le script de seed contient `ON CONFLICT DO NOTHING`, donc un PURGE préalable garantit un état propre. La table est `devices` — il n'existe PAS de table `events` (PDR-023).

---

## Critères de Done

- [ ] `device-api-local.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `device-api-distributed.yaml` : parseable par `ScenarioParser` sans exception
- [ ] `application-examples-local.yaml` : section `datasources.sut-db` + `http-targets.device-api` (base-url 8084) ajoutée
- [ ] `platform-app/src/main/resources/sql/seed-sut-devices.sql` créé (copie de V2__seed_10k_devices.sql, sans préfixe Flyway)
- [ ] `scriptPath: "classpath:sql/seed-sut-devices.sql"` dans les deux scénarios (PAS de `classpath:sql/V2__...`)
- [ ] Aucune URL inline dans les scénarios — y compris blocs `gatling:` (pas de `baseUrl:`, utiliser `target: device-api` + `path: submit-event`)
- [ ] Steps `dependsOn:` corrects (order : reset-devices → seed-devices → warmup/health → load → assertions)
- [ ] Step `reset-devices` (PURGE table `devices`) présent avant `seed-devices` (POPULATE) ; aucun step ne référence une table `events`
- [ ] `agentTags:` dans les steps du scénario distributed
- [ ] `.claude/progress.md` mis à jour : ISSUE-101 → DONE
