# PDR-019 — Deployment

**Module Maven** : `platform-deployment`
**Package** : (artefacts non-Java : Dockerfile, manifests K8s)
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/09-deployment.md` (complet), `.claude/knowledge/constraints.md` CD-01, CD-02, CD-03
**Dépend de** : PDR-018
**Issues** : ISSUE-083, ISSUE-084, ISSUE-085

---

## Responsabilité

Fournit les artefacts de déploiement : Dockerfile multi-stage (image unique <300MB),
docker-compose dev local (orchestrator + agents + Kafka + PostgreSQL), manifests Kubernetes
(StatefulSet orchestrateur, Deployment + HPA agents, ConfigMap, Secret, probes). Aucun code
Java métier — uniquement infrastructure de déploiement.

---

## Livrables (pas d'interface Java)

```
platform-deployment/
  docker/
    Dockerfile                 — eclipse-temurin:25-jre-alpine, user non-root, healthcheck
    docker-compose.yaml        — dev local : orchestrator, agent-1, agent-2, kafka, postgres
  kubernetes/
    orchestrator-statefulset.yaml
    agent-deployment.yaml
    agent-hpa.yaml
    configmap.yaml
    secret-template.yaml
    service.yaml
```

---

## Règles de Comportement

- Image base `eclipse-temurin:25-jre-alpine`, user non-root, taille < 300MB (CD-01).
- Healthcheck sur `/actuator/health` ; readiness/liveness probes K8s (CD-02).
- Mode/rôle via env vars (`RUNTIME_MODE`, `MODE`, `TRANSPORT_TYPE`) — prioritaires (ADR-006).
- Secrets via K8s Secret, jamais en clair (CNF-03).
- Orchestrateur = StatefulSet (replicas 1) ; agents = Deployment + HPA (CPU 70%, min 2 max 20).
- Java 25 + Virtual Threads (`-XX:+UseVirtualThreads`), `-XX:MaxRAMPercentage=75.0`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-018 → le JAR `performance-platform.jar` packagé dans l'image

Ce PDR est utilisé par : la chaîne CI/CD (hors périmètre code).
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] `docker build` produit une image < 300MB
- [ ] `docker-compose up` démarre orchestrateur + 2 agents + kafka + postgres
- [ ] Manifests K8s valides (`kubectl apply --dry-run`)
