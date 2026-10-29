# ISSUE-081 — Fichiers de config (local / orchestrator / agent) + sécurité

**PDR** : PDR-018
**Module** : `platform-bootstrap`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-078
**Estime** : M

---

## Objectif

Créer les 3 fichiers de configuration YAML + health
checks.

## Fichiers à Créer

```
bootstrap/src/main/resources/
  ├── application.yaml                — config commune + actuator
  ├── application-local.yaml          — runtime.mode=LOCAL, transport.type=IN_MEMORY
  ├── application-orchestrator.yaml   — runtime.role=ORCHESTRATOR
  └── application-agent.yaml          — runtime.role=AGENT
bootstrap/src/main/java/com/performance/platform/app/security/
  └── SecurityConfiguration.java      — OAuth2/JWT

bootstrap/src/test/java/com/performance/platform/app/
  └── ConfigProfilesTest.java         — chargement des profils
```

## Règles Spécifiques

- LOCAL : `runtime.mode=LOCAL`, `transport.type=IN_MEMORY`.
- ORCHESTRATOR/AGENT : `runtime.mode=DISTRIBUTED` + rôle.
- Datasources, transport, reporting configurés ; secrets via env (jamais en clair, CNF-03).
- API REST sécurisée
- Health `/actuator/health/{readiness,liveness}` exposés (CD-02).
- Pas de config GRPC (transport gRPC non implémenté).

## Critères de Done

- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] Les 3 profils chargent sans erreur
- [ ] readiness/liveness exposés
- [ ] Aucune référence transport GRPC
- [ ] `.claude/progress.md` mis à jour : ISSUE-081 → DONE
