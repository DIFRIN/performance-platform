# ISSUE-084 — docker-compose dev local (orchestrator + agents + kafka + postgres)

**PDR** : PDR-019
**Module** : `platform-deployment`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-083
**Estime** : S

---

## Objectif

Créer le `docker-compose.yaml` de développement local : orchestrateur, 2 agents, Kafka,
PostgreSQL.

## Fichiers à Créer

```
platform-deployment/docker/
  └── docker-compose.yaml
```

## Règles Spécifiques

- Services : `orchestrator` (MODE=ORCHESTRATOR, RUNTIME_MODE=DISTRIBUTED, TRANSPORT_TYPE=KAFKA), `agent-1`, `agent-2` (MODE=AGENT), `kafka`, `postgres`.
- `DB_URL` pointé vers le service postgres.
- Mode/rôle/transport via env vars (prioritaires).
- PAS de service ni config gRPC.

## Critères de Done

- [ ] `docker compose config` valide la syntaxe
- [ ] `docker compose up` démarre orchestrateur + 2 agents + kafka + postgres
- [ ] Aucune référence gRPC
- [ ] `progress.md` mis à jour : ISSUE-084 → DONE
