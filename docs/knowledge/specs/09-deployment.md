# Spec 09 — Deployment

**Module** : `distribution`
**Artefacts** : `docker/Dockerfile`, `example/docker-compose-{local,distributed}.yaml`,
`example/scenarios/*`, `example/sut/*`, `example/MODOP.md`

---

## 1. Image unique

Le Dockerfile multi-stage construit le fat JAR puis l'exécute sur un JRE Java 25 Alpine,
avec un utilisateur non-root (`perf`).

```dockerfile
# Build (depuis la racine du dépôt — le contexte doit inclure tous les modules)
docker build -f distribution/docker/Dockerfile -t performance-platform:latest .

# Stage build : eclipse-temurin:25-jdk-alpine + Maven
RUN mvn clean package -pl :platform-bootstrap -am -DskipTests -B -q

# Stage runtime : eclipse-temurin:25-jre-alpine
USER perf
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
EXPOSE 8080 9000
HEALTHCHECK ... /actuator/health
```

Points importants :
- **Virtual Threads sont natifs en Java 25** : aucun flag `-XX:+UseVirtualThreads`
  (il n'existe pas et faisait échouer le démarrage).
- Le `.dockerignore` à la racine exclut `target/`, `.git`, `.claude`, les exemples, etc.
- L'image est la même pour LOCAL, ORCHESTRATOR et AGENT ; le rôle est porté par
  `RUNTIME_MODE`/`MODE`.
- En AGENT, le conteneur n'expose aucun port applicatif (pas de Tomcat) ; n'exposer que
  ce qui est nécessaire au transport (généralement aucun port entrant).

### Variables d'environnement

| Variable | Valeurs | Description |
|---|---|---|
| `RUNTIME_MODE` | `LOCAL`, `DISTRIBUTED` | prioritaire sur `runtime.mode` |
| `MODE` | `ORCHESTRATOR`, `AGENT` | prioritaire sur `runtime.role` |
| `TRANSPORT_TYPE` | `IN_MEMORY`, `HTTP`, `KAFKA`, `CUSTOM` | prioritaire sur `transport.type` (ADR-056) |
| `AGENT_SUPPORTED_TASKS` | liste CSV | spécialisation d'agent (ADR-015) ; remplace `AGENT_TAGS` |
| `AGENT_ID` | string | identité stable (consumer group Kafka) |
| `KAFKA_BOOTSTRAP_SERVERS` | host:port | brokers |
| `IOT_KAFKA_SERVERS` | host:port | broker de la cible `iot-sut` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | — | PostgreSQL de la plateforme |
| `SUT_DB_URL` / `SUT_DB_USERNAME` / `SUT_DB_PASSWORD` | — | PostgreSQL du SUT (datasource `sut-db`) |
| `SUT_API_URL` | URL | cible HTTP `sut-api` |
| `WIREMOCK_URL` | URL | cible HTTP `wiremock` (WireMock EMBEDDED) |
| `REPORTING_OUTPUT_DIR` | chemin | sortie des rapports |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | URL | OAuth2/JWT (sécurité activée) |
| `SPRING_PROFILES_ACTIVE` | `local`, `orchestrator`, `agent`, `example` | profils Spring |

Règle (ADR-006) : une variable d'environnement définie gagne toujours sur la property
correspondante, via `RuntimeConfigEnvironmentPostProcessor`.

`WIREMOCK_URL` n'est nécessaire que sur l'agent qui résout la cible `wiremock`
(`direct-assertion`, `http-client` ou `gatling` avec `target: wiremock`). L'orchestrateur
n'exécute aucune tâche et n'en a pas besoin.

---

## 2. Exemple exécutable (`example/`)

Le seul exemple de déploiement conservé est `distribution/example/`. Le mode
opératoire complet est dans [`example/MODOP.md`](../../../distribution/example/MODOP.md).

| Fichier | Mode | Services |
|---|---|---|
| `docker-compose-local.yaml` | LOCAL | PostgreSQL, PostgreSQL SUT, Kafka, SUT, plateforme headless (un JVM) |
| `docker-compose-distributed.yaml` | DISTRIBUTED | PostgreSQL, PostgreSQL SUT, Kafka, SUT, orchestrateur, `mock-agent`, `agent-http`, `agent-kafka` |

Trois scénarios, **chacun valable dans les deux modes** : le mode est une propriété du
runtime, pas du fichier (ADR-053). Les anciennes paires `*-local` / `*-distributed` sont
fusionnées.

| Scénario | Flux | Assertion |
|---|---|---|
| `01-kafka-to-http.yaml` | Kafka → SUT → DB → HTTP vers `device_dns` | `direct-assertion` lisant le mock via `ref` |
| `02-http-to-kafka.yaml` | HTTP → SUT → DB → Kafka | `kafka-consumer` + `direct-assertion` |
| `03-assertion-model.yaml` | aucun SUT : fichier local + seuils d'injection | `direct-assertion` + `performance-thresholds` + SLI/SLO textuel |

Points structurants :
- **Aucun service WireMock standalone** : le mock est une **service step** `mock-server`
  (`deployment: EMBEDDED`, `during: injection`, port `8090`) hébergée par un agent de la
  plateforme. Elle est démarrée avant INJECTION et arrêtée après ; `drain` remplace les
  steps `shell sleep` (ADR-054).
- **Un seul SUT stub** : les comportements sont activés par configuration
  (`SUT_KAFKA_CONSUMER_ENABLED`, `SUT_HTTP_API_ENABLED`), avec la même image.
- **Un seul `application-local.yaml`** pour Docker LOCAL et l'IDE ; les URLs de
  conteneurs sont injectées par variables d'environnement, les défauts pointent vers
  `localhost`.
- En DISTRIBUTED, le profil `example` ajoute la datasource `sut-db` et la cible
  `sut-api` aux profils `orchestrator`/`agent`.
- Le load model est inline dans le scénario (ADR-053) ; les écarts de volume entre LOCAL
  et DISTRIBUTED ne justifient plus deux fichiers de scénario.

### Contraintes de déploiement distribué

- **Un seul orchestrateur par déploiement** (ADR-058). Deux instances ne sont pas
  supportées : pas de verrou distribué, pas de reprise après redémarrage, pas de haute
  disponibilité.
- **Un redémarrage de l'orchestrateur échoue les exécutions en cours** : au démarrage,
  toute exécution non terminale passe `FAILED` (raison `orchestrator restarted`) et
  `ScenarioFinished` est publié. Un long tir est perdu.
- Les agents peuvent redémarrer indépendamment ; ils se ré-enregistrent et repassent
  `IDLE`. L'agent qui héberge une service step fait échouer l'étape s'il est perdu
  (ADR-033, ADR-054).

### Spécialisation des agents (DISTRIBUTED)

```yaml
mock-agent:
  environment:
    AGENT_SUPPORTED_TASKS: "mock-server,direct-assertion"
    WIREMOCK_URL: "http://mock-agent:8090"
agent-http:
  environment:
    AGENT_SUPPORTED_TASKS: "gatling,http-client,performance-thresholds"
    SUT_API_URL: "http://sut:8084"
agent-kafka:
  environment:
    AGENT_SUPPORTED_TASKS: "database,direct-assertion,kafka-producer,kafka-consumer,shell"
    SUT_DB_URL: "jdbc:postgresql://postgres-sut:5432/sut_devices"
```

> `AGENT_TAGS` n'existe plus : le routage se fait uniquement par nom de tâche
> (`agent.supported-tasks`, ADR-015).

---

## 3. Modes de lancement

```bash
# LOCAL (défaut) — H2 en mémoire, transport IN_MEMORY
java -jar bootstrap/target/performance-platform.jar

# LOCAL avec les dépendances Docker de l'exemple (PostgreSQL/Kafka/SUT)
java -jar bootstrap/target/performance-platform.jar \
  --spring.profiles.active=local \
  --scenario=file:distribution/example/scenarios/01-kafka-to-http.yaml

# ORCHESTRATOR distribué (profil example = ressources SUT)
MODE=ORCHESTRATOR RUNTIME_MODE=DISTRIBUTED TRANSPORT_TYPE=KAFKA \
  SPRING_PROFILES_ACTIVE=orchestrator,example java -jar app.jar

# AGENT distribué (aucun serveur web)
MODE=AGENT RUNTIME_MODE=DISTRIBUTED TRANSPORT_TYPE=KAFKA \
  SPRING_PROFILES_ACTIVE=agent,example \
  AGENT_SUPPORTED_TASKS=mock-server,direct-assertion java -jar app.jar
```

---

## 4. Sécurité de déploiement

- `platform.security.enabled=false` : autorisé mais WARN explicite (posture permissive,
  ADR-038).
- `true` : `/api/**` exige un JWT avec rôle `USER` ou `ADMIN` ; endpoints publics
  `/actuator/health/**`, `/`, `/index.html`, `/assets/**`.
- Secrets jamais dans les YAML : variables d'environnement.
- API REST stateless, CSRF désactivé.
