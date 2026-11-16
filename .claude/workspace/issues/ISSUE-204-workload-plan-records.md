# ISSUE-204 — Records WorkloadPlan et codec

**PDR** : PDR-056
**Module** : `platform-domain`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Créer le modèle typé du plan de charge, son codec Map et son hash.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/injection/
  ├── ArrivalModel.java, LoadScope.java
  ├── Stage.java, Ramp.java, Hold.java, Burst.java, Pause.java
  ├── WorkloadPlan.java
  └── WorkloadPlanCodec.java        — fromMap / toMap / hash
core/platform-domain/src/test/java/com/performance/platform/domain/injection/
  ├── WorkloadPlanTest.java         — invariants
  └── WorkloadPlanCodecTest.java    — aller-retour, hash stable, erreurs
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- 0 annotation Spring/JPA/Jackson (ArchUnit).
- Invariants des records : durée > 0 (sauf Burst), niveaux >= 0, `users` > 0, liste non vide, `List.copyOf`.
- Forme canonique : `{model, scope, stages:[{ramp:{from,to,duration,measured}}, ...]}`, clés triées, durées ISO-8601.
- Hash = SHA-256 hex (`java.security.MessageDigest`) de la forme canonique.
- `fromMap` accepte les durées `30s`, `5m`, `1h` et ISO-8601.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Aller-retour `toMap(fromMap(x))` idempotent
- [ ] Deux plans égaux ont le même hash ; un champ modifié change le hash
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-204 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
