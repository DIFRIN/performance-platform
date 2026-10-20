# Operational Demo Stack

Runnable end-to-end stack for the Performance Engineering Platform.
It uses the same image for every role and exercises the three executor
families — **PostgreSQL**, **Kafka** and **mock HTTP (WireMock)** — in
both execution modes.

## Scenarios

| Scenario | Mode | Execution | Coverage |
|---|---|---|---|
| `scenarios/remote-demo.yaml` | DISTRIBUTED | orchestrator REST API + agent | `database`, `database-assertion`, `kafka-producer`, `kafka`, `http-client`, `http-mock` |
| `scenarios/local-demo.yaml` | LOCAL | headless CLI in a one-shot container | same six tasks, all in-process |

```
┌──────────────┐   REST /api/v1    ┌────────────────┐   Kafka    ┌──────────────────────┐
│   curl /     │ ────────────────► │  orchestrator  │ ─────────► │  agent-1             │
│  run-demo.sh │                   │  DISTRIBUTED   │            │  database / kafka /  │
└──────────────┘                   │  ORCHESTRATOR  │ ◄───────── │  http executors      │
                                   └───────┬────────┘  events    └──────┬───────────────┘
                                           │                           │
                     ┌─────────────────────┼───────────────────────────┘
                     ▼                     ▼
              ┌────────────┐        ┌────────────┐        ┌────────────┐
              │ PostgreSQL │        │   Kafka    │        │  WireMock  │
              └────────────┘        └────────────┘        └────────────┘

┌──────────────────┐
│  local-runner    │  same image, SPRING_PROFILES_ACTIVE=local
│  --scenario=...  │  in-memory transport, executors in-process
└──────────────────┘
```

## Quick start

Prerequisites: Docker + Docker Compose v2, `curl`, `python3`.

```bash
cd platform-deployment/examples/demo-stack
./run-demo.sh            # keep the stack up afterwards
./run-demo.sh --down     # tear down at the end
```

The script:

1. builds `performance-platform:demo` from the multi-stage Dockerfile;
2. starts PostgreSQL 16, Kafka (KRaft), WireMock and the orchestrator;
3. waits for `/actuator/health` and then starts `agent-1` (so it registers
   after the orchestrator has subscribed to Kafka lifecycle events);
4. waits for `/api/v1/agents` to be non-empty;
5. submits `scenarios/remote-demo.yaml` to `POST /api/v1/scenarios` and polls
   `/api/v1/executions/{id}` until terminal;
6. runs `scenarios/local-demo.yaml` through `docker compose run --rm local-runner`.

Expected result:

```
remote status : COMPLETED
local exit    : 0
```

## Manual usage

```bash
docker compose -f docker-compose.yaml up -d postgres kafka wiremock orchestrator
docker compose -f docker-compose.yaml up -d agent-1

curl -X POST http://localhost:8080/api/v1/scenarios \
  -H 'Content-Type: application/yaml' \
  --data-binary @scenarios/remote-demo.yaml

curl http://localhost:8080/api/v1/executions/<executionId>

docker compose -f docker-compose.yaml run --rm local-runner

docker compose -f docker-compose.yaml down -v
```

## Configuration notes

- **Security** is disabled for the demo (`PLATFORM_SECURITY_ENABLED=false`).
  The orchestrator logs a WARN on startup; that is expected (ADR-038).
- **Agent specialization** is configuration-driven (ADR-015):
  `AGENT_SUPPORTED_TASKS=database,database-assertion,kafka-producer,kafka,http-client,http-mock`.
- **Agent datasource**: the agent owns the datasource used by the `database`
  and `database-assertion` tasks (`PLATFORM_DATASOURCES_DEFAULT_*`).
  Flyway runs on the orchestrator; it is disabled on the agent
  (`SPRING_FLYWAY_ENABLED=false`) to avoid concurrent migrations.
- **Local runner**: the LOCAL profile normally uses H2, but the platform
  schema uses PostgreSQL types (`JSONB`, `TIMESTAMPTZ`). The local-runner
  service therefore points `platform.datasources.default` at the shared
  PostgreSQL. It still runs in LOCAL mode (in-memory transport, one JVM).
- **WireMock** mappings live in `wiremock/mappings/demo-api.json`
  (`GET /api/users` → 200, `POST /api/users` → 201).
- **Kafka topics** (`perf-demo-remote`, `perf-demo-local`) are auto-created.

## Outputs

- Execution state: `GET /api/v1/executions/{id}`
- Registered agents: `GET /api/v1/agents`
- WireMock admin: <http://localhost:8090/__admin/requests>
- Reports (orchestrator): `/var/lib/performance-platform/reports` inside the
  container (`REPORTING_OUTPUT_DIR`).
