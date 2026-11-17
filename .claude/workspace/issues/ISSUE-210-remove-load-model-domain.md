# ISSUE-210 — Suppression de LoadModel et LoadModelType

**PDR** : PDR-056
**Module** : `platform-domain`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-207, ISSUE-208
**Estime** : S (< 1h)

---

## Objectif

Supprimer les anciens types du domaine, désormais sans consommateur.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/injection/
  ├── LoadModel.java                — SUPPRIMER
  └── LoadModelType.java            — SUPPRIMER
(+ tests associés)
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Vérifier d'abord par grep qu'aucun module ne les référence ; sinon BLOQUER et signaler le module.
- `mvn clean install -DskipTests` doit compiler sur tout le projet.
- `interfaces-registry.md` : marquer ❌ REMOVED.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Build complet vert
- [ ] Aucune occurrence de `LoadModelType` dans le code (grep)
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-210 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
