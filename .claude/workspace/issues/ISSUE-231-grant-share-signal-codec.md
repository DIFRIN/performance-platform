# ISSUE-231 — Codec transport du GrantShare

**PDR** : PDR-060
**Module** : `platform-transport`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-227
**Estime** : S (< 1h)

---

## Objectif

Encoder et décoder `share` dans le signal de grant.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
infra/platform-transport/src/main/java/com/performance/platform/transport/ (AgentSignalDecoder + encodeur associé) — champs index, count, fraction (modification)
(+ test aller-retour)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Champ absent à la lecture => `GrantShare.single()` (tolérance de montée de version).
- Valable pour les trois transports (IN_MEMORY, HTTP, KAFKA) via le codec commun.

## Critères de Done

- [ ] `mvn test -pl :platform-transport -q` → 0 erreur
- [ ] Aller-retour d'un `TaskGrantSignal` avec share (2,4,0.25)
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-231 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
