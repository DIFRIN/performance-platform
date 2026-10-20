# Run from the IDE with Spring profiles

The platform processes run on the host; PostgreSQL, Kafka and WireMock run in
Docker. Three self-contained Spring profiles are provided in
`platform-app/src/main/resources/`:

| Profile file | Spring profile | Role | Task family |
|---|---|---|---|
| `application-ide-orchestrator.yaml` | `ide-orchestrator` | ORCHESTRATOR | dispatch only |
| `application-agent-injector.yaml` | `agent-injector` | AGENT | PREPARATION + INJECTION |
| `application-agent-asserter.yaml` | `agent-asserter` | AGENT | ASSERTION |

No environment variable is required: every endpoint points at `localhost`.

## 1. Start the Docker dependencies

```bash
docker compose -f platform-deployment/local-dev/docker-compose.yaml up -d --wait
```

| Service | Endpoint |
|---|---|
| PostgreSQL | `localhost:5432` — db `performance`, `postgres` / `changeme` |
| Kafka (KRaft) | `localhost:9092` |
| WireMock | `localhost:8090` |

Reset everything: `docker compose -f platform-deployment/local-dev/docker-compose.yaml down -v`.

## 2. Activate a profile

Pick the profile in your IDE's run configuration (IntelliJ: *Active profiles*;
VS Code: `-Dspring.profiles.active=...` argument or `spring.profiles.active`),
or run from the command line:

```bash
mvn -pl platform-app spring-boot:run -Dspring-boot.run.profiles=ide-orchestrator
mvn -pl platform-app spring-boot:run -Dspring-boot.run.profiles=agent-injector
mvn -pl platform-app spring-boot:run -Dspring-boot.run.profiles=agent-asserter
```

Equivalent forms:

```bash
java -jar platform-app/target/performance-platform.jar \
  --spring.profiles.active=ide-orchestrator

java -Dspring.profiles.active=agent-injector \
  -jar platform-app/target/performance-platform.jar
```

**Start the orchestrator first** (it runs Flyway and migrates the schema), then
one or both agents. The orchestrator exposes HTTP on `:8080`; the agents have
no web server (`WebApplicationType.NONE`, ADR-019).

## 3. How the profiles are wired

### `ide-orchestrator`

```yaml
runtime:   { mode: DISTRIBUTED, role: ORCHESTRATOR }
transport: { type: KAFKA, kafka.bootstrap-servers: localhost:9092 }
platform:
  security.enabled: false
  datasources.default: jdbc:postgresql://localhost:5432/performance
  http-targets.wiremock.base-url: http://localhost:8090
web.ui.enabled: true
reporting.output-directory: reports
```

### `agent-injector`

```yaml
runtime:   { mode: DISTRIBUTED, role: AGENT }
transport: { kafka.consumer-group: ide-agent-injector, bootstrap-servers: localhost:9092 }
agent.supported-tasks:
  - database
  - mock-server
  - kafka-producer
  - kafka-consumer
  - http-client
  - gatling
  - shell
  - filesystem
platform:
  security.enabled: false
  datasources.default: jdbc:postgresql://localhost:5432/performance
  http-targets.wiremock.base-url: http://localhost:8090
  kafka-clusters.default: localhost:9092
spring.flyway.enabled: false
```

### `agent-asserter`

```yaml
runtime:   { mode: DISTRIBUTED, role: AGENT }
transport: { kafka.consumer-group: ide-agent-asserter, bootstrap-servers: localhost:9092 }
agent.supported-tasks:
  - database-assertion
  - kafka
  - http-mock
  - wiremock
  - gatling-metric
  - file
platform:
  security.enabled: false
  datasources.default: jdbc:postgresql://localhost:5432/performance
  http-targets.wiremock.base-url: http://localhost:8090
  kafka-clusters.default: localhost:9092
spring.flyway.enabled: false
```

## 4. Submit a scenario

With the orchestrator and the two agents running:

```bash
curl -X POST http://localhost:8080/api/v1/scenarios \
  -H 'Content-Type: application/yaml' \
  --data-binary @platform-deployment/examples/demo-stack/scenarios/remote-demo.yaml

curl http://localhost:8080/api/v1/executions/<executionId>
curl http://localhost:8080/api/v1/agents
```

The demo scenario covers DB (`database` / `database-assertion`), Kafka
(`kafka-producer` / `kafka` assertion) and HTTP (`http-client` / `http-mock`).

## Notes

- The agent profiles are detected before Spring starts via their `agent-*`
  name, so the agents never start Tomcat.
- The consumer groups are stable per profile to avoid replaying old tasks when
  an agent restarts.
- `spring.flyway.enabled=false` on agents: the orchestrator owns migrations.
- Security is disabled in these profiles; the orchestrator logs a WARN
  describing the permissive posture (ADR-038).
- To run a single-JVM LOCAL scenario instead, use the existing `local` profile
  (`--spring.profiles.active=local`).
