# ISSUE-083 — Dockerfile multi-stage (image unique <300MB)

**PDR** : PDR-019
**Module** : `platform-deployment`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-077
**Estime** : M

---

## Objectif

Créer le module `platform-deployment` et le Dockerfile multi-stage produisant une image unique
non-root avec healthcheck.

## Fichiers à Créer

```
platform-deployment/docker/
  ├── Dockerfile
  └── .dockerignore
```

## Dockerfile (référence)

```dockerfile
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S perf && adduser -S perf -G perf
USER perf
WORKDIR /app
COPY --from=build /app/platform-app/target/performance-platform.jar app.jar
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1
EXPOSE 8080 9000
ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

## Règles Spécifiques

- Base `eclipse-temurin:25-jre-alpine`, user non-root (CD-01).
- Image < 300MB ; healthcheck sur `/actuator/health`.
- Java 25 + `-XX:+UseVirtualThreads`.
- Mode via env vars (RUNTIME_MODE/MODE/TRANSPORT_TYPE) — pas de gRPC dans les valeurs documentées.

## Critères de Done

- [ ] `docker build` réussit
- [ ] Image résultante < 300MB
- [ ] Container démarre, `/actuator/health` répond
- [ ] `progress.md` mis à jour : ISSUE-083 → DONE
