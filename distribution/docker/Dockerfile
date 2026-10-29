# Multi-stage Dockerfile — Performance Engineering Platform
#
# Build command (executed from project root):
#   docker build -f distribution/docker/Dockerfile -t performance-platform:latest .
#
# The build context MUST be the project root so that COPY paths resolve correctly.
# The .dockerignore file should be copied/symlinked to the project root before building.

# ========================================================================================
# STAGE 1: Build — compile le projet et produit le fat JAR
# ========================================================================================
FROM eclipse-temurin:25-jdk-alpine AS build

# Installer Maven (package Alpine officiel)
RUN apk add --no-cache maven

WORKDIR /app

# --- Couche 1 : descripteurs Maven (cache si les POMs ne changent pas) ---
COPY pom.xml .
COPY core/platform-domain/pom.xml core/platform-domain/pom.xml
COPY core/platform-plugin-api/pom.xml core/platform-plugin-api/pom.xml
COPY core/platform-use-cases/pom.xml core/platform-use-cases/pom.xml
COPY core/platform-transport-api/pom.xml core/platform-transport-api/pom.xml
COPY core/platform-report-api/pom.xml core/platform-report-api/pom.xml
COPY performance/platform-scenario-dsl/pom.xml performance/platform-scenario-dsl/pom.xml
COPY performance/platform-campaign-engine/pom.xml performance/platform-campaign-engine/pom.xml
COPY performance/platform-assertion/pom.xml performance/platform-assertion/pom.xml
COPY performance/platform-reporting/pom.xml performance/platform-reporting/pom.xml
COPY infra/platform-scenario-yaml/pom.xml infra/platform-scenario-yaml/pom.xml
COPY execution/platform-execution-engine/pom.xml execution/platform-execution-engine/pom.xml
COPY infra/platform-transport/pom.xml infra/platform-transport/pom.xml
COPY execution/platform-agent-runtime/pom.xml execution/platform-agent-runtime/pom.xml
COPY infra/platform-infrastructure/pom.xml infra/platform-infrastructure/pom.xml
COPY infra/platform-injection-gatling/pom.xml infra/platform-injection-gatling/pom.xml
COPY infra/platform-assertion-adapters/pom.xml infra/platform-assertion-adapters/pom.xml
COPY infra/platform-report-renderers/pom.xml infra/platform-report-renderers/pom.xml
COPY infra/platform-observability/pom.xml infra/platform-observability/pom.xml
COPY bootstrap/pom.xml bootstrap/pom.xml
COPY distribution/pom.xml distribution/pom.xml

# NOTE: dependency:resolve ne resout pas les modules SNAPSHOT du reactor
# (ils ne sont pas installes). La resolution se fait dans le build ci-dessous.

# --- Couche 2 : code source ---
COPY core/platform-domain/src core/platform-domain/src
COPY core/platform-plugin-api/src core/platform-plugin-api/src
COPY core/platform-use-cases/src core/platform-use-cases/src
COPY core/platform-transport-api/src core/platform-transport-api/src
COPY core/platform-report-api/src core/platform-report-api/src
COPY performance/platform-scenario-dsl/src performance/platform-scenario-dsl/src
COPY performance/platform-campaign-engine/src performance/platform-campaign-engine/src
COPY performance/platform-assertion/src performance/platform-assertion/src
COPY performance/platform-reporting/src performance/platform-reporting/src
COPY infra/platform-scenario-yaml/src infra/platform-scenario-yaml/src
COPY execution/platform-execution-engine/src execution/platform-execution-engine/src
COPY infra/platform-transport/src infra/platform-transport/src
COPY execution/platform-agent-runtime/src execution/platform-agent-runtime/src
COPY infra/platform-infrastructure/src infra/platform-infrastructure/src
COPY infra/platform-injection-gatling/src infra/platform-injection-gatling/src
COPY infra/platform-assertion-adapters/src infra/platform-assertion-adapters/src
COPY infra/platform-report-renderers/src infra/platform-report-renderers/src
COPY infra/platform-observability/src infra/platform-observability/src
COPY bootstrap/src bootstrap/src

# Builder le fat JAR (skip tests dans l'image Docker — les tests tournent dans la CI)
RUN mvn clean package -pl :platform-bootstrap -am -DskipTests -B -q

# ========================================================================================
# STAGE 2: Runtime — image minimale avec JRE Alpine uniquement
# ========================================================================================
FROM eclipse-temurin:25-jre-alpine AS runtime

# Securite CD-01 : utilisateur non-root obligatoire
RUN addgroup -S perf && adduser -S perf -G perf

WORKDIR /app

# Copier le fat JAR depuis le stage de build
COPY --from=build /app/bootstrap/target/performance-platform.jar app.jar

# Proprietaire du JAR = utilisateur non-root
RUN chown perf:perf app.jar

# Basculer vers l'utilisateur non-root
USER perf

# Health check CD-01 : Spring Boot Actuator -> /actuator/health
# --start-period=60s : delai de demarrage du contexte Spring
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

# Ports exposes :
# 8080 : API REST + Actuator health/readiness/liveness
# 9000 : Debug / management (optionnel)
EXPOSE 8080 9000

# JVM options Java 25 :
# Virtual Threads sont natifs depuis Java 21 (aucun flag necessaire).
# -XX:MaxRAMPercentage=75.0 : limite heap a 75% de la memoire container (graceful OOM)
# ADR-006 : le mode (LOCAL/DISTRIBUTED) et le role (ORCHESTRATOR/AGENT) sont definis
#           par les variables d'environnement RUNTIME_MODE / MODE, pas en dur ici
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
