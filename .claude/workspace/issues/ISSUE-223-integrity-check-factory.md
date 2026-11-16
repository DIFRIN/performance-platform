# ISSUE-223 — IntegrityCheckFactory

**PDR** : PDR-059
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-221, ISSUE-219
**Estime** : M (1-3h)

---

## Objectif

Produire les contrôles d'intégrité d'une étape d'injection à partir des métriques agrégées.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
performance/platform-assertion/src/main/java/com/performance/platform/assertion/
  ├── IntegrityCheckFactory.java
  └── IntegrityInput.java
performance/platform-assertion/src/test/java/com/performance/platform/assertion/IntegrityCheckFactoryTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Quatre contrôles : `arrival-deviation`, `agent-loss`, `incomplete-run`, `generator-saturation`.
- `generator-saturation` : sévérité WARNING, jamais bloquante.
- `arrivalKnown=false` => `arrival-deviation` INCONCLUSIVE.
- Chaque `Check` porte la tolérance appliquée et la valeur observée ; famille `INTEGRITY`.
- Fonction pure, sans Spring.

## Critères de Done

- [ ] `mvn test -pl :platform-assertion -q` → 0 erreur
- [ ] Table de vérité des quatre contrôles couverte par les tests
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-223 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
