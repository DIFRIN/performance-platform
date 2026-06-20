# Multi-stage Dockerfile — Performance Engineering Platform
#
# Build command (executed from project root):
#   docker build -f platform-deployment/docker/Dockerfile -t performance-platform:latest .
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
COPY platform-domain/pom.xml platform-domain/pom.xml
COPY platform-plugin-api/pom.xml platform-plugin-api/pom.xml
COPY platform-application/pom.xml platform-application/pom.xml
COPY platform-scenario-dsl/pom.xml platform-scenario-dsl/pom.xml
COPY platform-execution-engine/pom.xml platform-execution-engine/pom.xml
COPY platform-transport/pom.xml platform-transport/pom.xml
COPY platform-agent-runtime/pom.xml platform-agent-runtime/pom.xml
COPY platform-infrastructure/pom.xml platform-infrastructure/pom.xml
COPY platform-injection-gatling/pom.xml platform-injection-gatling/pom.xml
COPY platform-assertion/pom.xml platform-assertion/pom.xml
COPY platform-reporting/pom.xml platform-reporting/pom.xml
COPY platform-observability/pom.xml platform-observability/pom.xml
COPY platform-app/pom.xml platform-app/pom.xml

# Resoudre les dependances (couche stable tant que les POMs ne changent pas)
RUN mvn dependency:resolve -pl platform-app -am -B -q

# --- Couche 2 : code source ---
COPY platform-domain/src platform-domain/src
COPY platform-plugin-api/src platform-plugin-api/src
COPY platform-application/src platform-application/src
COPY platform-scenario-dsl/src platform-scenario-dsl/src
COPY platform-execution-engine/src platform-execution-engine/src
COPY platform-transport/src platform-transport/src
COPY platform-agent-runtime/src platform-agent-runtime/src
COPY platform-infrastructure/src platform-infrastructure/src
COPY platform-injection-gatling/src platform-injection-gatling/src
COPY platform-assertion/src platform-assertion/src
COPY platform-reporting/src platform-reporting/src
COPY platform-observability/src platform-observability/src
COPY platform-app/src platform-app/src

# Builder le fat JAR (skip tests dans l'image Docker — les tests tournent dans la CI)
RUN mvn clean package -pl platform-app -am -DskipTests -B -q

# ========================================================================================
# STAGE 2: Runtime — image minimale avec JRE Alpine uniquement
# ========================================================================================
FROM eclipse-temurin:25-jre-alpine AS runtime

# Securite CD-01 : utilisateur non-root obligatoire
RUN addgroup -S perf && adduser -S perf -G perf

WORKDIR /app

# Copier le fat JAR depuis le stage de build
COPY --from=build /app/platform-app/target/performance-platform.jar app.jar

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
# -XX:+UseVirtualThreads : activation Project Loom Virtual Threads (tout I/O bloquant)
# -XX:MaxRAMPercentage=75.0 : limite heap a 75% de la memoire container (graceful OOM)
# ADR-006 : le mode (LOCAL/DISTRIBUTED) et le role (ORCHESTRATOR/AGENT) sont definis
#           par les variables d'environnement RUNTIME_MODE / MODE, pas en dur ici
ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
