# Example — Mode Opératoire

Two end-to-end scenarios, each runnable in **LOCAL** (one platform JVM) or
**DISTRIBUTED** (orchestrator + specialised agents). The mock HTTP server is
never a separate service: it is WireMock **EMBEDDED** in the platform
(`mock-server` task) and bound to port `8090`.

```
distribution/example/
├── docker-compose-local.yaml          # LOCAL platform in one container + SUT + infra
├── docker-compose-distributed.yaml    # orchestrator + mock-agent + agent-http + agent-kafka + SUT + infra
├── sut/                               # one stub image, behaviours enabled by configuration
├── sut-db/sql/                        # devices schema + 10k seed
├── scenarios/                         # 2 scenarios x 2 modes
└── MODOP.md                           # this file
```

The platform profiles involved:

| Mode | Spring profile(s) | Config file |
|---|---|---|
| LOCAL | `local` | `bootstrap/src/main/resources/application-local.yaml` |
| DISTRIBUTED | `orchestrator,example` / `agent,example` | `application-orchestrator.yaml` / `application-agent.yaml` + `application-example.yaml` |

The **same** `application-local.yaml` is used by the Docker LOCAL container and
by an IDE run: Docker overrides the `*_URL` variables with service names, while
the defaults (`localhost:5432/5433/9092/8084/8090`) work from the IDE.

Optional HTTP/Kafka/RabbitMQ/Socket transport mTLS and independent one-way UI
HTTPS are documented in [tls/README.md](tls/README.md). The TLS demo uses its own
Compose project and leaves the existing example stack unchanged.

---

## 1. Scenarios

All four scenarios declare `metadata.executionGroup: example-sut`. Within one
orchestrator, runs in this group wait before preparation, protecting the shared
mock port and SUT database. Different groups (or no group) can run concurrently.
This does not coordinate multiple orchestrators or separate LOCAL JVMs.
The API persists queued runs immediately; steps stay pending until the group
is acquired (`execution_group_wait` / `execution_group_acquired` in logs).

Execution-scoped embedded mocks are cleaned after report generation,
including on failure or cancellation, even if the scenario's STOP step was
skipped. A remote mock START can wait for asynchronous cleanup up to its timeout.
Upgrade orchestrator and agents together and re-upload the scenario YAML to
update already stored scenarios. Recreating mock-agent once releases mocks
leaked by the previous version; no volume deletion is required.
New Kafka agent consumer groups start on future tasks/signals only; they no
longer replay historical START commands when an agent is recreated. The agent
registers only after both command subscriptions are ready.

### Scenario 1 — Kafka injection → SUT → DB → HTTP → embedded mock

```
platform --kafka-producer--> iot-commands
                                  |
                                  v
                           SUT consumer
                                  |
                    SELECT device_dns FROM devices
                                  |
                                  v
                 HTTP POST http://<device_dns>/command
                                  |
                                  v
                    embedded WireMock (port 8090)
                                  |
                     http-mock assertion (receivedCalls)
```

1. `mock-server` STARTS embedded WireMock on port 8090.
2. `database` PURGE + POPULATE seeds 10 000 devices; `device_dns` holds the
   mock address (`host.docker.internal:8090`).
3. `kafka-producer` produces commands into `iot-commands`.
4. The SUT consumes, finds the device in PostgreSQL, reads `device_dns`, and
   POSTs to `http://<device_dns>/command`.
5. `http-mock` asserts the embedded mock received calls.

### Scenario 2 — HTTP injection → SUT endpoint → DB → Kafka → consumer assertion

```
platform --gatling HTTP--> POST /api/events?device_id=...
                                  |
                                  v
                           SUT HTTP API
                                  |
                    SELECT EXISTS(...) FROM devices
                                  |
                                  v
                     Kafka produce -> device-events
                                  |
                     kafka-consumer COUNT + kafka assertion
```

1. `database` PURGE + POPULATE seeds the devices.
2. Gatling injects `POST /api/events?device_id=device-NNNN` on the SUT.
3. The SUT checks the device in PostgreSQL and publishes to `device-events`.
4. `kafka-consumer` COUNTs the topic; `kafka` asserts the expected volume.

---

## 2. Prerequisites

- Docker + Docker Compose v2
- ~4 GB RAM
- Ports free: `5432`, `5433`, `8080`, `8084`, `8090`, `9092`

---

## 3. Build the images

From the repository root:

```bash
docker compose -f distribution/example/docker-compose-local.yaml build
# or, once, for both compose files:
docker build -f distribution/docker/Dockerfile -t performance-platform:example .
docker build -f distribution/example/sut/Dockerfile -t performance-sut:example distribution/example/sut
```

### Certificate errors during Docker builds

For `PKIX path building failed` behind a TLS-inspecting proxy, the preferred
fix is to trust the proxy/company CA in the build JVM. As a temporary workaround
in a trusted development environment, explicitly disable Maven certificate and
hostname verification for **both** image builds. The same flag also disables
certificate verification for the Alpine `apk` command installing Maven in the
platform builder (`TLS: server certificate not trusted`):

```bash
MAVEN_TLS_INSECURE=true docker compose -f distribution/example/docker-compose-distributed.yaml up --build -d
# LOCAL mode uses the same build argument:
MAVEN_TLS_INSECURE=true docker compose -f distribution/example/docker-compose-local.yaml build
```

PowerShell:

```powershell
$env:MAVEN_TLS_INSECURE = "true"
docker compose -f distribution\example\docker-compose-distributed.yaml up --build -d
Remove-Item Env:\MAVEN_TLS_INSECURE
```

For a direct `docker build`, pass `--build-arg MAVEN_TLS_INSECURE=true`.
Verification stays enabled by default (`false`). The flag covers dependency
prefetch and packaging, including parent POM resolution. Maven uses the Wagon
transport because its `maven.wagon.http.ssl.*` flags do not apply to the native
Resolver HTTP transport. HTTPS encryption remains enabled; server identity
verification is bypassed, exposing dependency downloads to interception.
Do not use this workaround for production builds. No application/runtime TLS
settings are changed. Rebuild without the flag (use `--no-cache` to replace
previously cached build layers) after configuring the CA.

---

## 4. LOCAL mode

### 4.1 Run from Docker (default)

Start the infrastructure and the SUT once:

```bash
docker compose -f distribution/example/docker-compose-local.yaml up -d \
  postgres postgres-sut kafka sut
```

Scenario 1:

```bash
docker compose -f distribution/example/docker-compose-local.yaml \
  run --rm -e SCENARIO_FILE=01-kafka-to-http-local.yaml platform
```

Scenario 2:

```bash
docker compose -f distribution/example/docker-compose-local.yaml \
  run --rm -e SCENARIO_FILE=02-http-to-kafka-local.yaml platform
```

The `platform` container is a run-and-exit headless CLI (ADR-021). Exit code
`0` means the scenario completed and all assertions passed; `1`/`2` means a
failed assertion or a technical error. Reports are written to
`distribution/example/reports/`.

The `reports-init` one-shot service assigns the bind-mounted `reports/`
directory to the image's `perf` user and checks that it is writable before the
LOCAL UI or DISTRIBUTED orchestrator starts. Only initialization runs as root;
the platform remains non-root. No `chmod 777` or recursive ownership change is
used. If the host filesystem refuses the ownership change, initialization
fails and blocks platform startup; check `docker compose ... logs reports-init`.

Reset between runs:

```bash
docker compose -f distribution/example/docker-compose-local.yaml down -v
```

### 4.2 Run from the IDE (same `application-local.yaml`)

Keep only the infrastructure and the SUT running:

```bash
docker compose -f distribution/example/docker-compose-local.yaml up -d \
  postgres postgres-sut kafka sut
```

Then run the platform from the IDE or CLI with the `local` profile and a
scenario file:

```bash
java -jar bootstrap/target/performance-platform.jar \
  --spring.profiles.active=local \
  --scenario=file:distribution/example/scenarios/01-kafka-to-http-local.yaml
```

```bash
java -jar bootstrap/target/performance-platform.jar \
  --spring.profiles.active=local \
  --scenario=file:distribution/example/scenarios/02-http-to-kafka-local.yaml
```

The defaults in `application-local.yaml` resolve to `localhost`:

| Resource | Default |
|---|---|
| Platform DB (H2 by default; Docker overrides to PostgreSQL) | in-memory |
| SUT DB | `localhost:5433/sut_devices` |
| Kafka | `localhost:9092` |
| SUT HTTP API | `http://localhost:8084` |
| Embedded WireMock | `http://localhost:8090` |

Port `8090` must be free: the IDE process binds it when the scenario starts the
mock. The SUT container reaches it via `host.docker.internal:8090`.

---

## 5. DISTRIBUTED mode

Start the whole stack:

```bash
docker compose -f distribution/example/docker-compose-distributed.yaml up -d
```

Wait for the orchestrator:

```bash
curl http://localhost:8080/actuator/health
```

Check the agents:

```bash
curl http://localhost:8080/api/v1/agents
```

Scenario 1:

```bash
curl -X POST http://localhost:8080/api/v1/scenarios \
  -H 'Content-Type: application/yaml' \
  --data-binary @distribution/example/scenarios/01-kafka-to-http-distributed.yaml
```

Scenario 2:

```bash
curl -X POST http://localhost:8080/api/v1/scenarios \
  -H 'Content-Type: application/yaml' \
  --data-binary @distribution/example/scenarios/02-http-to-kafka-distributed.yaml
```

Follow the execution with the returned `executionId`:

```bash
curl http://localhost:8080/api/v1/executions/<executionId>
```

Reports are written to `distribution/example/reports/`.

If an existing deployment has `/reports` owned by root and reports return 404,
apply the updated Compose configuration without deleting database volumes:

```bash
docker compose -f distribution/example/docker-compose-distributed.yaml up -d reports-init orchestrator
docker compose -f distribution/example/docker-compose-distributed.yaml exec orchestrator \
  sh -ec 'id; ls -ld /reports; test -w /reports'
```

For LOCAL, use `docker-compose-local.yaml` and `platform-ui` instead of
`orchestrator`. Rerun the scenario afterwards: fixing permissions does not
regenerate reports for past executions. Check `report_generated` or
`report_generation_failed` in platform logs for the new execution ID.

Reset:

```bash
docker compose -f distribution/example/docker-compose-distributed.yaml down -v
```

### Agent specialisation

| Agent | `AGENT_SUPPORTED_TASKS` | Role |
|---|---|---|
| `mock-agent` | `mock-server,http-mock` | starts embedded WireMock, asserts request counts |
| `agent-http` | `gatling,http-client` | HTTP injection and SUT health checks |
| `agent-kafka` | `database,database-assertion,kafka-producer,kafka-consumer,kafka,shell` | DB, Kafka injection/consumer assertions, wait steps |

Only `mock-agent` receives `WIREMOCK_URL`; it is the only agent resolving the
`wiremock` HTTP target. `agent-http` receives `SUT_API_URL`; `agent-kafka`
receives `SUT_DB_URL`. The orchestrator executes no task and receives neither.

After restarting only the orchestrator, running agents reappear on their next
heartbeat (normally within 10 seconds, plus Kafka consumer startup). Heartbeats
carry the registration metadata needed to rebuild the in-memory registry.
When upgrading an older deployment, rebuild and recreate **both** orchestrator
and agents once; old agents send heartbeats without this metadata:

```bash
docker compose -f distribution/example/docker-compose-distributed.yaml up -d --build \
  orchestrator mock-agent agent-http agent-kafka
```

For an older image, restarting the agents also republishes their initial
registration. This is a workaround, not a requirement after upgrading:

```bash
docker compose -f distribution/example/docker-compose-distributed.yaml restart mock-agent agent-http agent-kafka
```

---

## 6. SUT stub: combined or split by configuration

The SUT image contains both behaviours. Configuration decides which are active:

| Variable | Default | Effect |
|---|---|---|
| `SUT_KAFKA_CONSUMER_ENABLED` | `true` | Kafka consumer → DB → HTTP call |
| `SUT_HTTP_API_ENABLED` | `true` | `POST /api/events` → DB → Kafka |
| `SUT_KAFKA_GROUP_ID` | `sut-consumer` | Kafka consumer group |
| `SUT_DB_URL` | `jdbc:postgresql://localhost:5433/sut_devices` | SUT database |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `SUT_COMMAND_PATH` | `/command` | HTTP path called on the device address |

Combined (default, used by both compose files): both variables `true`.

Split into two instances of the same image:

```yaml
sut-consumer:
  image: performance-sut:example
  environment:
    SUT_KAFKA_CONSUMER_ENABLED: "true"
    SUT_HTTP_API_ENABLED: "false"
    SPRING_MAIN_WEB_APPLICATION_TYPE: none
    # + DB / Kafka / extra_hosts

sut-api:
  image: performance-sut:example
  environment:
    SUT_KAFKA_CONSUMER_ENABLED: "false"
    SUT_HTTP_API_ENABLED: "true"
    # + DB / Kafka
```

Add more behaviours the same way: a new `@ConditionalOnProperty`-guarded
component plus a configuration flag. No new image or compose service is needed
unless you choose to split the instances.

---

## 7. Where the device address comes from

`devices.device_dns` stores the endpoint the SUT must call. In this example it
is the embedded WireMock published on the host:

```
device_dns = host.docker.internal:8090
```

The SUT prepends `http://` and appends `/command`. The mock port is published by
`platform` (LOCAL) or `mock-agent` (DISTRIBUTED), so the SUT container reaches
it through the host gateway in both modes, and the IDE process reaches it on
`localhost:8090`. Replace the seeded value with a fixed IP if your environment
provides one.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unknown http-target: 'sut-api'` | wrong Spring profile | use `local` for LOCAL, `agent,example` / `orchestrator,example` for DISTRIBUTED |
| SUT never calls the mock | `device_dns` unreachable | verify `host.docker.internal` resolves and port `8090` is published |
| `Unknown http-target: 'wiremock'` on an agent | `WIREMOCK_URL` missing on the agent that executes `http-mock` | set it on `mock-agent` only |
| Scenario 2 assertion too high | HTTP injection ramp too short | lower the `value` in `assert-events-produced` or lengthen the ramp |
| Kafka assertion counts old messages | topic not reset | `docker compose ... down -v` before the run |
