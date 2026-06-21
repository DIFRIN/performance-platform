# ISSUE-099 — docker-compose-sut.yaml (5 services SUT)

**PDR** : PDR-024
**Module** : `platform-deployment/examples/`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-096 + ISSUE-097 + ISSUE-098 (services SUT et DB schema doivent être DONE)
**Estime** : S

---

## Objectif

Créer le fichier `docker-compose-sut.yaml` avec les 5 services SUT qui constituent l'environnement "Système Sous Test" pour les exemples IoT.

Ce fichier est lancé **séparément** du `docker-compose.yaml` de la plateforme. Les ports SUT sont non-conflictuels avec les ports de la plateforme.

Créer également le stub WireMock JSON pour simuler les endpoints IoT.

---

## Fichiers à Créer

```
platform-deployment/examples/
  ├── docker-compose-sut.yaml
  └── wiremock/
      └── mappings/
          └── iot-endpoints.json
```

---

## Contenu `docker-compose-sut.yaml`

Voir PDR-024 section "docker-compose-sut.yaml" pour le contenu complet.

Points clés à respecter :
- **Ports SUT** : 9093 (kafka-sut), 5433 (postgres-sut), 8082 (device-api), 8083 (iot-dispatcher), 8090 (wiremock)
- **Réseau** : `sut-net` bridge dédié — ne pas partager avec le réseau plateforme
- **`docker-entrypoint-initdb.d`** : monter `../../platform-examples/sut-db/sql` pour l'init automatique
- **WireMock volume** : `./wiremock:/home/wiremock` pour les stubs
- **healthchecks** : tous les services dépendants attendent `service_healthy`
- **`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`** : les topics `iot-commands` et `device-events` sont créés automatiquement au premier message

---

## Contenu `wiremock/mappings/iot-endpoints.json`

```json
{
  "mappings": [
    {
      "name": "IoT Command Endpoint",
      "priority": 1,
      "request": {
        "method": "POST",
        "urlPattern": "/command"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "body": "{\"status\":\"accepted\",\"processed\":true}"
      }
    },
    {
      "name": "IoT Health Check",
      "request": {
        "method": "GET",
        "url": "/health"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "body": "{\"status\":\"UP\"}"
      }
    },
    {
      "name": "WireMock Admin Health (proxy)",
      "request": {
        "method": "GET",
        "url": "/__admin/health"
      },
      "response": {
        "status": 200,
        "body": "{\"status\":\"healthy\"}"
      }
    }
  ]
}
```

---

## Règles Spécifiques

- **Port 9093 pour kafka-sut** : éviter le conflit avec le Kafka de la plateforme (port 9092).
- **Port 5433 pour postgres-sut** : éviter le conflit avec le PostgreSQL de la plateforme (port 5432).
- **`PLAINTEXT_HOST://0.0.0.0:9093`** : accessible depuis la plateforme (hors Docker) sur `localhost:9093`. Le listener interne reste `PLAINTEXT://kafka-sut:29092`.
- **Build context** : utiliser des chemins relatifs depuis l'emplacement du fichier `docker-compose-sut.yaml` (dans `platform-deployment/examples/`). Les Dockerfiles sont dans `../../platform-examples/iot-dispatcher/` et `../../platform-examples/device-api/`.
- **Volume `sut_postgres_data`** : volume nommé pour persistance entre redémarrages. `docker compose down -v` pour supprimer les données.

---

## Critères de Done

- [ ] `docker compose -f platform-deployment/examples/docker-compose-sut.yaml config` → 0 erreur YAML
- [ ] Services : postgres-sut, kafka-sut, wiremock, iot-dispatcher, device-api
- [ ] Ports non-conflictuels avec le docker-compose.yaml de la plateforme
- [ ] WireMock stub `POST /command → 200` présent dans `iot-endpoints.json`
- [ ] Volume `sut_postgres_data` déclaré
- [ ] Réseau `sut-net` bridge dédié
- [ ] `.claude/progress.md` mis à jour : ISSUE-099 → DONE
