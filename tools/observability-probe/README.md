# Observability probe

Annex tool: replays Grafana and Kibana panel queries and exposes the resulting
series as platform assertions.

- Standalone CLI: `java -jar observability-probe.jar {catalog|query|verify|render|install-plugin}`
- Platform plugin: `panel-assertion` (drop the JAR in `platform.plugins.dir`)

Full documentation: [docs/knowledge/tools/observability-probe.md](../../docs/knowledge/tools/observability-probe.md).

## Build

The plugin classes compile against the platform contracts, so install them first:

```bash
# repository root
mvn -q -DskipTests install
# annex
cd tools/observability-probe
mvn -q clean package
```

The produced `target/observability-probe.jar` is self-contained (Jackson,
SnakeYAML shaded).

## Quick start

```bash
export GRAFANA_URL=https://grafana.example.com
export GRAFANA_TOKEN=glsa_xxx

java -jar target/observability-probe.jar verify --source grafana-prod --dashboard api-slo
java -jar target/observability-probe.jar query \
  --source grafana-prod --dashboard api-slo --panel 12 \
  --from now-30m --to now \
  --map refId=A:injection.latency.p95:ms
```
