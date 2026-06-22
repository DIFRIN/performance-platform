# ISSUE-108 — Update README with Mock-as-Agent architecture documentation

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : APPROVED
**Priorite** : P2 (normal — documentation, non bloquant)
**Bloquee par** : ISSUE-103, ISSUE-104, ISSUE-105, ISSUE-106, ISSUE-107 (documente les artefacts crees et le cleanup effectue)
**Estime** : M (1-3h) — etendue S a M car documentation du modele configuration-driven

---

## Objectif

Mettre a jour le `README.md` existant dans `platform-deployment/examples/` pour documenter :

1. L'architecture "Mock as Agent" (WireMock EMBEDDED dans un agent — PAS de service standalone)
2. Le modele **configuration-driven** pour la specialisation des agents : `agent.supported-tasks` (pas d'auto-discovery, pas de `agentTags`)
3. La difference entre mode LOCAL (tout in-process, specialisation non-pertinente) et DISTRIBUTED (agents specialises par config)
4. La suppression des executors device du module production
5. Le nouveau demo Mock Agent avec docker-compose-wiremock-agent.yaml
6. Les walkthroughs pour le mode LOCAL et DISTRIBUTED du mock-agent
7. L'explication de la specialisation des agents via configuration (pas via annotations)
8. Les sorties attendues (rapports dans ./reports/)

## Fichier a Modifier

```
platform-deployment/examples/
  └── README.md
```

## Contenu attendu

Le README doit etre reorganise avec les sections suivantes :

```markdown
# Exemples — Performance Engineering Platform

Deux types de demos :
- **Demo 1: IoT SUT** — use cases reels avec services SUT (iot-dispatcher, device-api)
- **Demo 2: Mock as Agent** — WireMock demarre EMBEDDED par un agent specialise

---

## Modele de Specialisation des Agents (Configuration-Driven)

La plateforme utilise un modele **entierement configuration-driven** pour la specialisation des agents :

```yaml
# application.yml (ou variable d'environnement AGENT_SUPPORTED_TASKS)
agent:
  supported-tasks:
    - mock-server
    - http-client
```

**Comment ca marche :**
1. Chaque agent declare explicitement ses `supported-tasks` dans sa configuration
2. Au demarrage, l'agent construit `AgentDescriptor.supportedTaskNames` depuis cette config
3. L'orchestrateur broadcast chaque `TaskExecutionRequest` a tous les agents (pas de selection ciblee)
4. Chaque agent filtre localement via `TaskSpecializationFilter` : si la task est dans `supportedTaskNames`, l'agent l'execute ; sinon, il l'ignore

**Ce qui N'EST PAS utilise pour le routing :**
- Les annotations `@Preparation`/`@Injection`/`@Assertion` ne derivent PAS les `supportedTaskNames` — elles servent uniquement au PluginLoader pour la resolution task-name -> implementation
- Les `agentTags` sont COMPLETEMENT SUPPRIMES — aucun routing par tags
- Pas d'auto-discovery des taches disponibles sur un agent

### Mode LOCAL vs DISTRIBUTED

| Aspect | LOCAL | DISTRIBUTED |
|---|---|---|
| Instances JVM | 1 seule (tout in-process) | Plusieurs (orchestrateur + N agents) |
| Specialisation | Non-pertinente — LocalAgent a tous les supportedTaskNames | Critique — chaque agent a sa liste explicite |
| `agent.supported-tasks` | Ignore en pratique (tout est disponible) | Obligatoire — definit le role de l'agent |
| `agentTags` dans les scenarios | Non (pas de dispatch) | Non (supprime — routing par task name) |
| Configuration | `--spring.profiles.active=local` | `MODE=AGENT` + `AGENT_SUPPORTED_TASKS=...` |
| Dispatch | Aucun — execution directe in-process | Broadcast Kafka vers tous les agents |
| Filtrage | `LocalAgent.canExecute()` retourne toujours `true` | `TaskSpecializationFilter` verifie `supportedTaskNames` |

---

## Architecture

### Demo 1: IoT SUT

[Meme diagramme existant, sans le service wiremock]

### Demo 2: Mock as Agent

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

Pas de service WireMock standalone — le mock est un agent specialise.
Chaque agent a une config AGENT_SUPPORTED_TASKS explicite.
Meme image Docker partout, config differente = role different.

---

## Prerequis

- Docker + Docker Compose v2
- Java 25 + Maven 3.9+ (pour builder l'image)
- 4 Go RAM disponibles

---

## Demo 1: IoT SUT (SUT-A iot-dispatcher + SUT-B device-api)

### Demarrage du SUT

```bash
docker compose -f platform-deployment/examples/docker-compose-sut.yaml up -d
```

Ports exposes :
| Service | Port | Description |
|---|---|---|
| kafka-sut | 9093 | Kafka SUT (topics: iot-commands, device-events) |
| postgres-sut | 5433 | PostgreSQL SUT (10k devices pre-charges) |
| iot-dispatcher | 8083 | SUT-A : consomme Kafka -> dispatch HTTP |
| device-api | 8084 | SUT-B : recoit HTTP -> publie Kafka |

[Commandes de lancement des scenarios existantes conservees]

---

## Demo 2: WireMock as Agent (Mock Server)

Cette demo montre comment un agent specialise demarre un WireMock EMBEDDED
en reponse a un step de preparation. Pas de service WireMock standalone.

### Specialisation des agents (configuration-driven)

Chaque agent declare ses taches via la configuration, pas via des annotations ou des tags.

**mock-agent** — `AGENT_SUPPORTED_TASKS=mock-server,http-client` :
- Demarre WireMock in-process (EMBEDDED), port auto-assign
- Mappings charges depuis le classpath
- Peut aussi agir comme client HTTP

**gatling-agent** — `AGENT_SUPPORTED_TASKS=gatling,gatling-metric` :
- Execute les simulations d'injection de charge HTTP avec Gatling
- Peut aussi evaluer les metriques Gatling (assertions gatling-metric)

**standard-agent** — `AGENT_SUPPORTED_TASKS=http-mock,database,kafka` :
- Execute les assertions (gatling-metric, http-mock)
- Interroge le mock-agent a distance pour verifier les compteurs
- Peut executer des operations database et kafka

### Demarrage

```bash
# 1. Builder l'image Docker de la plateforme
docker build -f platform-deployment/docker/Dockerfile -t performance-platform:latest .

# 2. Demarrer la plateforme avec agents specialises
docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml up -d

# 3. Verifier que tout est healthy (~90s)
curl http://localhost:8080/actuator/health
```

### Lancer les scenarios

```bash
# Mode LOCAL (tout in-process, MockServer EMBEDDED)
# Specialisation non-pertinente — LocalAgent a tous les supportedTaskNames
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/http-api-mock-agent-local.yaml

# Mode DISTRIBUTED (agents specialises par configuration)
# Routing par task name — broadcast + TaskSpecializationFilter cote agent
curl -X POST http://localhost:8080/scenarios/run \
  -H "Content-Type: application/yaml" \
  --data-binary @platform-deployment/examples/scenarios/http-api-mock-agent-distributed.yaml
```

### Deroulement du workflow (DISTRIBUTED)

1. L'orchestrateur recoit le scenario et planifie le DAG
2. Step `spawn-mock` (task: mock-server) -> dispatch broadcast
3. Chaque agent recoit la `TaskExecutionRequest` et filtre via `TaskSpecializationFilter`
4. mock-agent filtre -> RESPONSIBLE (mock-server est dans supportedTaskNames) -> demarre WireMock EMBEDDED
5. gatling-agent et standard-agent filtrent -> NOT_RESPONSIBLE -> ignorent
6. mock-agent publie `TaskCompleted` avec outputs `{port: 35421, url: "http://mock-agent:35421"}`
7. Step `load-test` (task: gatling) -> dispatch broadcast -> seul gatling-agent accepte
8. Gatling envoie du trafic HTTP vers `http://mock-agent:35421/api/users`
9. Steps d'assertion (gatling-metric, http-mock) -> dispatch broadcast -> standard-agent accepte
10. standard-agent verifie metriques Gatling + compteurs WireMock
11. Step `stop-mock` (task: mock-server) -> dispatch broadcast -> mock-agent accepte -> arrete proprement WireMock

### Rapports

Les rapports sont generes dans le volume `./reports/` (monte sur l'orchestrateur) :
- `./reports/{executionId}/report.html` — rapport HTML
- `./reports/{executionId}/report.json` — rapport JSON
- `./reports/{executionId}/report.pdf` — rapport PDF

---

## Arreter

```bash
# Arreter le SUT IoT
docker compose -f platform-deployment/examples/docker-compose-sut.yaml down -v

# Arreter la plateforme Mock Agent
docker compose -f platform-deployment/examples/docker-compose-wiremock-agent.yaml down -v
```
```

## Regles Specifiques

- **Conserver** toute la documentation existante de la Demo 1 (IoT SUT)
- **Supprimer** toute reference au service `wiremock` standalone (port 8090, service wiremock dans les diagrammes)
- **Supprimer** la section "Device Simulation: Example-Only" si elle existe — le code device est maintenant supprime, pas documente
- Remplacer les references `device-check-perf.yaml` si presentes
- **Documenter le modele configuration-driven** :
  - Expliquer `agent.supported-tasks` comme seule source de verite pour la specialisation
  - Expliquer que les annotations `@Preparation`/`@Injection`/`@Assertion` servent UNIQUEMENT au PluginLoader
  - Expliquer que `agentTags` est COMPLETEMENT SUPPRIME
  - Expliquer la difference LOCAL (specialisation non-pertinente) vs DISTRIBUTED (specialisation par config)
  - Documenter le flow de routing : broadcast -> TaskSpecializationFilter -> RESPONSIBLE/NOT_RESPONSIBLE
- **Documenter `AGENT_SUPPORTED_TASKS` env var** : format (virgule-separe), mapping vers `agent.supported-tasks`
- Les deux demos sont complementaires — IoT demontre les SUT, Mock Agent demontre l'architecture agent specialise + modele configuration-driven
- Verifier que tous les chemins de fichiers references existent

## Criteres de Done

- [ ] README.md contient la section "Modele de Specialisation des Agents (Configuration-Driven)"
- [ ] README.md contient le tableau LOCAL vs DISTRIBUTED
- [ ] README.md contient la section "Demo 2: WireMock as Agent" avec diagramme
- [ ] README.md contient le walkthrough LOCAL et DISTRIBUTED pour mock-agent
- [ ] README.md explique la specialisation par configuration (`agent.supported-tasks`)
- [ ] README.md explique que `agentTags` est supprime et que le routing est par task name
- [ ] README.md explique le role des annotations (PluginLoader uniquement, pas de derivation des supportedTaskNames)
- [ ] Aucune reference a "wiremock" en tant que service standalone
- [ ] Aucune reference a `device-population` ou `device-check` comme tasks
- [ ] Aucune reference a `device-check-perf.yaml`
- [ ] Aucune reference a `AGENT_TAGS` ou `agentTags`
- [ ] Tous les chemins de fichiers references existent
- [ ] Les commandes curl et docker sont correctes
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-108 -> DONE
