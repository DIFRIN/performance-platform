# ISSUE-053 — ArchUnit : JPA confiné + domaine pur

**PDR** : PDR-012
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-052
**Estime** : S

---

## Objectif

Compléter le test ArchUnit pour garantir que les annotations JPA (`jakarta.persistence`)
n'apparaissent QUE dans `..infrastructure.persistence..` et jamais ailleurs.

## Fichiers à Créer / Modifier

```
platform-infrastructure/src/test/java/com/performance/platform/infrastructure/arch/
  └── PersistenceConfinementTest.java
```

## Règles à Vérifier (ArchUnit)

- `jakarta.persistence..` importé uniquement dans `..infrastructure.persistence..`.
- Les entities ne sont jamais retournées par une méthode publique d'un adapter (toujours des records domaine).
- `..persistence..` n'est pas importé par `..executor..`, `..publisher..`, `..plugin..`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test passe : aucune fuite d'entity JPA hors du package persistence
- [ ] `progress.md` mis à jour : ISSUE-053 → DONE
