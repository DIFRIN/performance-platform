# ISSUE-116 — Replace AGENT_TAGS with AGENT_SUPPORTED_TASKS in ALL deployment files

**PDR** : PDR-026
**Module** : `platform-deployment`
**Statut** : APPROVED
**Priorite** : P0 (cleanup obligatoire ADR-015)
**Bloquee par** : ISSUE-111 (AgentProperties doit exister, car c'est la cible du mapping)
**Estime** : M (1-3h)

---

## Objectif

Remplacer CHAQUE occurrence de `AGENT_TAGS` par `AGENT_SUPPORTED_TASKS` dans tous les fichiers de deploiement. Mettre a jour les valeurs : au lieu de tags libres (`"dev,standard"`), utiliser des noms de task reconnus (`"mock-server,http-client"`). Apres cette Issue, `grep -r AGENT_TAGS` dans tout le projet ne doit retourner AUCUN resultat dans les fichiers de deploiement (`.yaml`, `.yml`).

## Fichiers a Modifier

### 1. `platform-deployment/docker/docker-compose.yaml`

Deux occurrences (ligne ~125 et ~150) :

**Avant** :
```yaml
      AGENT_TAGS: "dev,standard"
```
```yaml
      AGENT_TAGS: "dev,high-memory"
```

**Apres (agent-1)** :
```yaml
      AGENT_SUPPORTED_TASKS: "mock-server,http-client"
```

**Apres (agent-2)** :
```yaml
      AGENT_SUPPORTED_TASKS: "gatling,mock-server,http-client"
```

### 2. `platform-deployment/kubernetes/agent-deployment.yaml`

Ligne 54 :

**Avant** :
```yaml
            # ---- Agent tags for observability (ADR-008) ----
            - name: AGENT_TAGS
              value: "k8s,standard"
```

**Apres** :
```yaml
            # ---- Agent supported tasks (ADR-015: configuration-driven) ----
            # Task names this agent can execute. Override per deployment.
            # Format: comma-separated task names (e.g., "mock-server,http-client,gatling")
            - name: AGENT_SUPPORTED_TASKS
              value: "mock-server,http-client,gatling"
```

### 3. Autres fichiers

Verifier aussi :
- `platform-deployment/kubernetes/configmap.yaml` (si `AGENT_TAGS` present)
- `platform-deployment/kubernetes/secret-template.yaml` (si `AGENT_TAGS` present)
- Tout fichier `*.yaml` ou `*.yml` sous `platform-deployment/`
- Tout fichier `.env`, `.properties`, ou script Shell qui reference `AGENT_TAGS`

### 4. Fichiers qui contiennent `agentTags` dans les scenarios YAML

Les scenarios DISTRIBUTED dans `platform-deployment/examples/scenarios/` contiennent `agentTags` dans leurs steps. Ces champs `agentTags` n'ont jamais ete supportes dans les specs du parser YAML -- ils sont des commentaires/documentation. Action :

- **Supprimer** les lignes `agentTags: [kafka, standard]` etc.
- **Transformer** les commentaires qui mentionnent `agentTags` pour expliquer le modele configuration-driven a la place.

Fichiers concernes :
- `platform-deployment/examples/scenarios/iot-dispatcher-distributed.yaml`
- `platform-deployment/examples/scenarios/device-api-distributed.yaml`

Chaque fichier a des commentaires comme :
```yaml
    # agentTags: [kafka, standard] — distribue sur un agent specialise kafka
```

Remplacer par :
```yaml
    # Cet agent doit avoir "kafka-producer" dans son agent.supported-tasks
```

Et supprimer les champs `agentTags:` actifs (non commentes) dans ces scenarios.

## Verification post-modification

```bash
# Doit retourner ZERO resultat
grep -r "AGENT_TAGS" platform-deployment/

# Doit retourner ZERO resultat dans les fichiers YAML
grep -r "agentTags:" platform-deployment/**/*.yaml

# Verifier que AGENT_SUPPORTED_TASKS est present
grep -r "AGENT_SUPPORTED_TASKS" platform-deployment/
```

## Regles Specifiques

- Les valeurs de `AGENT_SUPPORTED_TASKS` doivent etre des noms de task valides (ex: `mock-server`, `gatling`, `http-client`, `kafka-producer`, etc.) -- pas des tags libres.
- Pour `docker-compose.yaml` agent-1 : `mock-server,http-client` (agent mock + HTTP)
- Pour `docker-compose.yaml` agent-2 : `gatling,mock-server,http-client` (agent avec Gatling + extras)
- Pour `agent-deployment.yaml` : `mock-server,http-client,gatling` (agent K8s standard)
- Les commentaires doivent referencer ADR-015 (pas ADR-008).
- Les scenarios YAML DISTRIBUTED doivent expliquer que la distribution est configuration-driven, pas via des `agentTags` dans le YAML.

## Critères de Done

- [ ] `grep -r "AGENT_TAGS" platform-deployment/` retourne ZERO resultat
- [ ] `grep -r "agentTags:" platform-deployment/**/*.yaml` retourne ZERO resultat
- [ ] `grep -r "AGENT_SUPPORTED_TASKS" platform-deployment/` retourne les fichiers modifies
- [ ] Les valeurs d'env var sont des noms de task valides (pas des tags)
- [ ] Les commentaires referencent ADR-015
- [ ] Les scenarios DISTRIBUTED n'ont plus de champ `agentTags` actif
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-116 → DONE
