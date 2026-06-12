# ISSUE-085 — Manifests Kubernetes (StatefulSet + Deployment + HPA)

**PDR** : PDR-019
**Module** : `platform-deployment`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-083
**Estime** : M

---

## Objectif

Créer les manifests Kubernetes : StatefulSet orchestrateur, Deployment + HPA agents, ConfigMap,
Secret template, Service, avec probes readiness/liveness.

## Fichiers à Créer

```
platform-deployment/kubernetes/
  ├── orchestrator-statefulset.yaml
  ├── agent-deployment.yaml
  ├── agent-hpa.yaml
  ├── configmap.yaml
  ├── secret-template.yaml
  └── service.yaml
```

## Règles Spécifiques

- Orchestrateur : StatefulSet replicas 1, env `MODE=ORCHESTRATOR`, secret `DB_PASSWORD`.
- Agents : Deployment replicas 2, env `MODE=AGENT`, resources requests/limits (512Mi/2Gi).
- HPA : CPU 70%, min 2 max 20.
- Readiness `/actuator/health/readiness`, liveness `/actuator/health/liveness` (CD-02).
- ConfigMap pour la config applicative, Secret pour les credentials (CNF-03).
- PAS de config gRPC.

## Critères de Done

- [ ] `kubectl apply --dry-run=client -f kubernetes/` valide tous les manifests
- [ ] Probes readiness/liveness présentes
- [ ] HPA min 2 / max 20 / CPU 70%
- [ ] Aucune référence gRPC
- [ ] `progress.md` mis à jour : ISSUE-085 → DONE
