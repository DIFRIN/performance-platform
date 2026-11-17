# ISSUE-203 — Test d'intégration worker en mode LOCAL

**PDR** : PDR-055
**Module** : `platform-bootstrap`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-202
**Estime** : M (1-3h)

---

## Objectif

Vérifier de bout en bout qu'un scénario LOCAL lance un worker, produit un résultat, et qu'une annulation l'arrête.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
bootstrap/src/test/java/com/performance/platform/e2e/
  └── InjectionWorkerIT.java        — run nominal + annulation + deux injections concurrentes
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Profil Maven `integration-tests`.
- SUT = WireMock embarqué ; charge minimale (quelques secondes).

## Critères de Done

- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] `mvn verify -P integration-tests -pl :platform-bootstrap` → vert
- [ ] Deux injections concurrentes sur un agent : la seconde n'est pas exécutée en parallèle
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-203 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
