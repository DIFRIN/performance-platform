#!/usr/bin/env bash
# =============================================================================
# Operational demo runner
# =============================================================================
# 1. builds the platform image
# 2. starts PostgreSQL, Kafka, WireMock and the orchestrator
# 3. starts the agent and waits for its registration
# 4. submits the DISTRIBUTED scenario through the REST API
# 5. runs the LOCAL scenario through the headless CLI
#
# Usage:
#   ./run-demo.sh          # keep the stack running for inspection
#   ./run-demo.sh --down   # tear down (volumes included) at the end
# =============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE=(docker compose -f "$DIR/docker-compose.yaml")
DOWN=false
if [[ "${1:-}" == "--down" ]]; then
  DOWN=true
fi

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

if port_in_use 8080; then
  fail "port 8080 is already in use (another orchestrator?)"
fi
if port_in_use 9092; then
  echo "WARN: port 9092 is already in use — Kafka may conflict with an existing broker."
fi

step "Building the platform image (first build may take several minutes)"
"${COMPOSE[@]}" build

step "Starting PostgreSQL, Kafka, WireMock and orchestrator"
"${COMPOSE[@]}" up -d postgres kafka wiremock orchestrator

step "Waiting for the orchestrator health endpoint"
for _ in $(seq 1 120); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 \
  || fail "orchestrator did not become healthy (docker compose logs orchestrator)"

step "Starting the agent (after the orchestrator subscribed to Kafka lifecycle events)"
"${COMPOSE[@]}" up -d agent-1

step "Waiting for the agent registration"
AGENT_COUNT=0
for _ in $(seq 1 120); do
  agents_json="$(curl -fsS http://localhost:8080/api/v1/agents 2>/dev/null || true)"
  if [[ -n "$agents_json" ]]; then
    AGENT_COUNT="$(python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    print(len(data) if isinstance(data, list) else len(data.get("agents", [])))
except Exception:
    print(0)
' <<<"$agents_json")"
  fi
  [[ "$AGENT_COUNT" -gt 0 ]] && break
  sleep 2
done
[[ "$AGENT_COUNT" -gt 0 ]] || fail "no agent registered (docker compose logs agent-1)"

step "Submitting the DISTRIBUTED scenario"
submit_json="$(curl -fsS -X POST http://localhost:8080/api/v1/scenarios \
  -H 'Content-Type: application/yaml' \
  --data-binary @"$DIR/scenarios/remote-demo.yaml")"
echo "$submit_json"
EXEC_ID="$(python3 -c '
import json, sys
print(json.load(sys.stdin)["executionId"])
' <<<"$submit_json")"

step "Waiting for remote execution $EXEC_ID"
REMOTE_STATUS=""
for _ in $(seq 1 300); do
  status_json="$(curl -fsS "http://localhost:8080/api/v1/executions/$EXEC_ID" 2>/dev/null || true)"
  if [[ -n "$status_json" ]]; then
    REMOTE_STATUS="$(python3 -c '
import json, sys
try:
    print(json.load(sys.stdin).get("status", ""))
except Exception:
    print("")
' <<<"$status_json")"
    printf '  status=%s\n' "$REMOTE_STATUS"
  fi
  case "$REMOTE_STATUS" in
    COMPLETED|FAILED|CANCELLED) break ;;
  esac
  sleep 2
done
[[ "$REMOTE_STATUS" == "COMPLETED" || "$REMOTE_STATUS" == "FAILED" || "$REMOTE_STATUS" == "CANCELLED" ]] \
  || fail "remote execution did not reach a terminal status"
echo "$status_json"

step "Running the LOCAL scenario via the headless CLI"
set +e
"${COMPOSE[@]}" run --rm local-runner
LOCAL_EXIT=$?
set -e

step "Result"
printf '  remote status : %s\n' "$REMOTE_STATUS"
printf '  local exit    : %s\n' "$LOCAL_EXIT"
printf '  orchestrator  : http://localhost:8080\n'
printf '  wiremock      : http://localhost:8090/__admin\n'
printf '  exec id       : %s\n' "$EXEC_ID"

if [[ "$DOWN" == true ]]; then
  step "Tearing down the stack"
  "${COMPOSE[@]}" down -v
fi

if [[ "$REMOTE_STATUS" != "COMPLETED" || "$LOCAL_EXIT" -ne 0 ]]; then
  exit 1
fi
