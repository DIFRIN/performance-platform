# Observability probe — standalone image (annex tool, not part of the platform image).
#
# Build the JAR first (the plugin classes need the platform contracts):
#   mvn -q -DskipTests install            # repository root
#   cd tools/observability-probe && mvn -q clean package
#   docker build -t observability-probe:local tools/observability-probe
#
# Run:
#   docker run --rm -e GRAFANA_URL -e GRAFANA_TOKEN observability-probe:local \
#     query --source grafana-prod --dashboard api-slo --panel 12 \
#           --from now-30m --to now --map refId=A:injection.latency.p95:ms

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app
COPY target/observability-probe.jar /app/observability-probe.jar

ENTRYPOINT ["java", "-jar", "/app/observability-probe.jar"]
