# ISSUE-237 — Test d'intégration : scope global distribué

**PDR** : PDR-060
**Module** : `platform-bootstrap`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-233, ISSUE-236
**Estime** : M (1-3h)

---

## Objectif

Vérifier en distribué (2 agents, transport mémoire ou HTTP) qu'un plan global est partagé et que `minAgents` bloque le run.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
bootstrap/src/test/java/com/performance/platform/e2e/
  └── GlobalScopeDistributedIT.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Profil `integration-tests`.
- Assertions : somme des requêtes des deux agents ≈ total du plan (±5 %) ; `minAgents: 3` avec 2 agents => FAILED sans trafic.

## Critères de Done

- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] `mvn verify -P integration-tests -pl :platform-bootstrap` → vert
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-237 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
