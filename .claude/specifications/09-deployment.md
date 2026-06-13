# Spec 09 — Deployment

**Module** : `platform-deployment`

---

## 1. Docker

### Image Unique

```dockerfile
# Dockerfile
FROM eclipse-temurin:25-jre-alpine AS runtime

# Sécurité : user non-root
RUN addgroup -S perf && adduser -S perf -G perf
USER perf

WORKDIR /app

COPY --from=build /app/platform-app/target/performance-platform.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

EXPOSE 8080 9000

ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

### Variables d'Environnement

| Variable | Valeurs | Priorité | Description |
|---|---|---|---|
| `RUNTIME_MODE` | `LOCAL`, `DISTRIBUTED` | **Prioritaire** sur `runtime.mode` property | Mode d'exécution |
| `MODE` | `ORCHESTRATOR`, `AGENT` | **Prioritaire** sur `runtime.role` property | Rôle en mode DISTRIBUTED |
| `TRANSPORT_TYPE` | `SOCKET`, `RABBITMQ`, `KAFKA`, `GRPC` | **Prioritaire** sur `transport.type` property | Transport |
| `DB_URL` | jdbc URL | Prioritaire sur `spring.datasource.url` | URL PostgreSQL |
| `DB_USER` | string | Prioritaire sur datasource username | User DB |
| `DB_PASSWORD` | string | Prioritaire — via Secret K8s | Password DB |

> **Règle** : env var définie → elle gagne toujours, quelle que soit la valeur dans `application.yaml`.
> `application.yaml` fournit les valeurs par défaut pour le développement local.

---

## 2. Docker Compose (Dev Local)

```yaml
# docker-compose.yaml
services:
  orchestrator:
    image: performance-platform:latest
    environment:
      MODE: ORCHESTRATOR
      RUNTIME_MODE: DISTRIBUTED
      TRANSPORT_TYPE: KAFKA
      DB_URL: jdbc:postgresql://postgres:5432/perf
    ports:
      - "8080:8080"
    depends_on: [kafka, postgres]

  agent-1:
    image: performance-platform:latest
    environment:
      MODE: AGENT
      RUNTIME_MODE: DISTRIBUTED
      TRANSPORT_TYPE: KAFKA
      AGENT_TAGS: europe,standard
    depends_on: [kafka, orchestrator]

  agent-2:
    image: performance-platform:latest
    environment:
      MODE: AGENT
      AGENT_TAGS: europe,high-memory
    depends_on: [kafka, orchestrator]

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: perf
      POSTGRES_USER: perf
      POSTGRES_PASSWORD: perf
    ports:
      - "5432:5432"
```

---

## 3. Kubernetes

### Orchestrator (StatefulSet)
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: performance-orchestrator
spec:
  replicas: 1
  selector:
    matchLabels:
      app: performance-orchestrator
  template:
    spec:
      containers:
        - name: orchestrator
          image: performance-platform:latest
          env:
            - name: MODE
              value: ORCHESTRATOR
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: perf-db-secret
                  key: password
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
```

### Agent (Deployment + HPA)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: performance-agent
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: agent
          image: performance-platform:latest
          env:
            - name: MODE
              value: AGENT
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: performance-agent-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: performance-agent
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```
