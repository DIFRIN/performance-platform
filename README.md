# Performance Engineering Platform

A distributed performance engineering platform built on Java 25 and Spring Boot 4.
Runs as a single artifact in standalone mode or as an orchestrator coordinating
specialized agents across multiple VMs, clusters, and network zones.

---

## What It Does

The platform executes **performance campaigns** — sequences of preparation, load
injection, and assertion steps defined in YAML. Each campaign produces an evidence
report (HTML, PDF, JSON) and can publish it to Confluence, S3, SharePoint, Git, or Nexus.

A campaign follows three mandatory phases in order:

```
PREPARATION  →  INJECTION  →  ASSERTION
```

Within each phase, steps are resolved as a **DAG**: independent steps run in parallel,
dependent steps wait for their prerequisites. The orchestrator drives execution
step-by-step — no bulk dispatch.

---

## Modes

A single JAR. The runtime mode is controlled by environment variables.

| `RUNTIME_MODE` | `MODE` | Description |
|---|---|---|
| `LOCAL` | — | Everything runs in one JVM: orchestrator + all executors |
| `DISTRIBUTED` | `ORCHESTRATOR` | Drives the campaign, dispatches tasks to remote agents |
| `DISTRIBUTED` | `AGENT` | Receives and executes tasks for its declared specializations |

Environment variables take priority over `application.yaml`.

### Local mode

```bash
RUNTIME_MODE=LOCAL java -jar performance-platform.jar
```

### Distributed mode

```bash
# Start the orchestrator
RUNTIME_MODE=DISTRIBUTED MODE=ORCHESTRATOR java -jar performance-platform.jar

# Start agents — each declares what it can execute
RUNTIME_MODE=DISTRIBUTED MODE=AGENT java -jar performance-platform.jar
```

---

## Scenario DSL

Campaigns are described in YAML.

```yaml
scenario:
  id: customer-api-perf
  name: Customer API Campaign
  version: 1.0.0
  tags:
    - performance
    - regression
  metadata:
    owner: team-api
    jira: PERF-123

  execution:
    mode: DISTRIBUTED
    taskAvailabilityTimeoutSeconds: 120

  steps:
    - id: purge-db
      task: database
      phase: PREPARATION
      requiredContexts: []
      dependsOn: []
      parameters:
        operation: PURGE
        datasource: customer-db
      timeout: 30s

    - id: start-mock
      task: mock-server
      phase: PREPARATION
      requiredContexts: []
      dependsOn: []
      parameters:
        deployment: EMBEDDED
        port: 8090
        mappingsPath: wiremock/mappings

    - id: inject
      task: gatling
      phase: INJECTION
      requiredContexts:
        - purge-db
        - start-mock
      dependsOn:
        - purge-db
        - start-mock
      parameters:
        simulation: com.example.CustomerApiSimulation
        loadModel: api-load
      timeout: 20m

    - id: p95-check
      task: gatling-metric
      phase: ASSERTION
      requiredContexts:
        - inject
      dependsOn:
        - inject
      parameters:
        metric: p95
        operator: LT
        value: 500

    - id: error-rate
      task: gatling-metric
      phase: ASSERTION
      requiredContexts:
        - inject
      dependsOn:
        - inject
      parameters:
        metric: errorRate
        operator: LT
        value: 1.0

loadModels:
  api-load:
    type: RAMP
    stages:
      - duration: 2m
        usersPerSecond: 10
      - duration: 5m
        usersPerSecond: 100
      - duration: 5m
        usersPerSecond: 300
      - duration: 2m
        usersPerSecond: 0
```

### Load model types

| Type | Description |
|---|---|
| `RAMP` | Linear ramp through stages, each with a target rate and duration |
| `CONSTANT` | Fixed rate for a given duration |
| `RAMP_UP_DOWN` | Ramp up → hold → ramp down |
| `SPIKE` | Base rate with a sudden spike to a peak |
| `STAIR` | Stepped increments at regular intervals |
| `SOAK` | Long-duration constant load with a warm-up ramp |
| `BURST` | Repeated bursts of high load separated by quiet periods |
| `CUSTOM` | Arbitrary profile defined as time/rate points |

---

## Built-in Task Types

### Preparation

| Task name | Description |
|---|---|
| `database` | Purge, populate, migrate, backup, or restore a datasource |
| `filesystem` | Create, delete, upload, or clean up files |
| `shell` | Run an arbitrary shell command |
| `docker` | Start, stop, or pull a Docker image |
| `kafka-consumer` | Monitor, consume, or count messages on a Kafka topic |
| `kafka-producer` | Pre-load messages onto a Kafka topic |
| `mock-server` | Start or stop a WireMock instance (embedded or external) |

### Injection

| Task name | Description |
|---|---|
| `gatling` | Run a Gatling simulation. Supported protocols: HTTP/S, WebSocket, Kafka, JMS, gRPC |

### Assertion

| Task name | Description |
|---|---|
| `gatling-metric` | Assert on a Gatling metric: `p50`, `p95`, `p99`, `errorRate`, `throughput` |
| `database` | Assert on a SQL query result |
| `kafka` | Assert on produced count, consumed count, or consumer lag |
| `http-mock` | Assert on WireMock received or matched call counts |
| `file` | Assert on file existence or checksum |

---

## Distributed Agents

In `DISTRIBUTED` mode, each agent declares the tasks it can handle:

```yaml
agent:
  id: agent-perf-eu-01
  name: "EU Performance Agent"
  supportedTasks:
    - gatling
    - gatling-metric
  capabilities:
    maxConcurrentTasks: 3
    version: "1.0.0"
  heartbeat:
    intervalSeconds: 10
    ttlSeconds: 30
  orchestrator:
    url: http://orchestrator:8080
```

The orchestrator broadcasts each task to all agents. Each agent decides locally
whether it handles the task based on its `supportedTasks` list. Multiple agents
can claim the same task — the orchestrator tracks all results independently.

### Transport options

| Type | Config value | Mechanism |
|---|---|---|
| In-memory | `IN_MEMORY` | Used automatically in `LOCAL` mode |
| TCP Socket | `SOCKET` | Direct TCP connections |
| RabbitMQ | `RABBITMQ` | FANOUT exchange — all agents receive every task message |
| Kafka | `KAFKA` | One consumer group per agent — each agent reads all messages |
| gRPC | `GRPC` | Streaming-based, supports mTLS |

Switch transport by changing one variable:

```bash
TRANSPORT_TYPE=KAFKA  java -jar performance-platform.jar
TRANSPORT_TYPE=GRPC   java -jar performance-platform.jar
```

---

## Plugin System

Extend the platform without modifying its source. Drop a JAR into the `plugins/`
directory and restart.

```java
// Declare the task type with one annotation
@Preparation(name = "my-db-seeder", description = "Seeds the ACME legacy database")
public class AcmeDbSeeder implements TaskExecutor {

    @Override
    public String getSupportedTaskName() { return "my-db-seeder"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        var connectionString = (String) step.parameters().get("connectionString");
        // ...
    }
}
```

Use `@Preparation`, `@Injection`, or `@Assertion` to declare the task phase.
The `name` attribute maps directly to the `task:` field in scenario YAML.

Depend only on `platform-plugin-api`:

```xml
<dependency>
    <groupId>com.performance</groupId>
    <artifactId>platform-plugin-api</artifactId>
    <version>${platform.version}</version>
    <scope>provided</scope>
</dependency>
```

If two plugins declare the same `name`, the external plugin takes precedence.
Invalid JARs are skipped with a warning — they do not crash the platform.

---

## Reports

Every campaign produces a report aggregating preparation results, Gatling metrics,
assertion verdicts, and a global `SUCCESS / WARNING / FAILED` outcome.

### Formats

| Format | File | Contents |
|---|---|---|
| HTML | `reports/<id>/campaign.html` | Full report with embedded Gatling graphs |
| PDF | `reports/<id>/campaign.pdf` | Print-ready version generated from HTML |
| JSON | `reports/<id>/campaign.json` | Machine-readable full report |

### Publishers

Configure one or more destinations:

```yaml
reporting:
  formats: [HTML, PDF, JSON]
  publishers:
    - target: CONFLUENCE
      properties:
        url: https://company.atlassian.net
        spaceKey: PERF
        token: ${CONFLUENCE_TOKEN}
    - target: S3
      properties:
        bucket: perf-reports
        region: eu-west-1
```

Available targets: `CONFLUENCE`, `S3`, `SHAREPOINT`, `GIT`, `NEXUS`.

---

## Observability

Every execution exposes:

- **Metrics** via Micrometer: `execution_duration`, `task_duration`, `task_failures_total`
- **Traces** via OpenTelemetry: one span per task and per phase
- **Structured JSON logs** with `executionId`, `scenarioId`, `taskId`, `agentId`, `phase`

Health endpoint: `GET /actuator/health`

---

## Quick Start

### Prerequisites

- Java 25
- Docker (for local stack)
- Maven 3.9+

### Build

```bash
mvn clean install
```

### Run locally

```bash
RUNTIME_MODE=LOCAL java -jar platform-app/target/performance-platform.jar
```

Submit a scenario:

```bash
curl -X POST http://localhost:8080/api/v1/scenarios/execute \
  -H "Content-Type: application/yaml" \
  --data-binary @scenario.yaml
```

### Run with Docker Compose

```bash
docker compose up
```

This starts an orchestrator, two agents, Kafka, and PostgreSQL.

---

## Configuration Reference

| Variable | Values | Default | Description |
|---|---|---|---|
| `RUNTIME_MODE` | `LOCAL`, `DISTRIBUTED` | `LOCAL` | Execution mode |
| `MODE` | `ORCHESTRATOR`, `AGENT` | `LOCAL` | Role in distributed mode |
| `TRANSPORT_TYPE` | `SOCKET`, `RABBITMQ`, `KAFKA`, `GRPC` | — | Message transport |
| `DB_URL` | JDBC URL | — | PostgreSQL connection |
| `DB_USER` | string | — | Database user |
| `DB_PASSWORD` | string | — | Database password (use K8s Secret) |

All variables override their `application.yaml` equivalents.

---

## Architecture

```
platform-domain/           Pure domain — no framework dependency
platform-application/      Use cases and ports (interfaces)
platform-infrastructure/   Adapters: DB, transport, publishers
platform-scenario-dsl/     YAML parser and scenario validation
platform-execution-engine/ DAG orchestration, retry, phase management
platform-agent-runtime/    Agent lifecycle, specialization filter
platform-transport/        ExecutionTransport + 4 implementations
platform-injection-gatling/ Gatling runner and load model translation
platform-assertion/        Assertion executors
platform-reporting/        Report engine and publishers
platform-plugin-api/       Public API for external plugins
platform-observability/    Micrometer and OpenTelemetry configuration
platform-deployment/       Dockerfile and Kubernetes manifests
platform-app/              Spring Boot entry point
```

---

## License

[To be defined]
