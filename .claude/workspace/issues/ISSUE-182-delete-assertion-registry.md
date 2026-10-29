# ISSUE-182 — Delete AssertionExecutorRegistry

**PDR** : PDR-036
**Module** : `platform-assertion`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-181
**Estime** : M (1-3h)

---

## Objectif

Finaliser la migration assertion (A2) : supprimer `AssertionExecutorRegistry`,
`DefaultAssertionExecutorRegistry` et `UnsupportedAssertionNameException` (si plus
référencé), et adapter les tests. Le registre unifié `DefaultTaskExecutorRegistry`
est désormais la seule source de résolution.

> ADR requise (ADR-030) : suppression de l'interface publique `AssertionExecutorRegistry`.

## Fichiers à Supprimer / Modifier

```
infra/platform-assertion-adapters/src/main/java/com/performance/platform/assertion/
  ├── AssertionExecutorRegistry.java           — SUPPRIMER
  ├── DefaultAssertionExecutorRegistry.java    — SUPPRIMER
  └── UnsupportedAssertionNameException.java   — SUPPRIMER (si plus utilisé)

infra/platform-assertion-adapters/src/test/java/com/performance/platform/assertion/
  ├── DefaultAssertionExecutorRegistryTest.java — SUPPRIMER
  └── AssertionTaskNameUniquenessTest.java      — conserver (garantit l'unicité des noms d'assertion)
```

## Règles Spécifiques

- Vérifier qu'aucun bean/import restant ne référence le registre supprimé
  (grep `AssertionExecutorRegistry` dans tout le repo).
- Les `AssertionExecutor` restent des beans `TaskExecutor` collectés par
  `DefaultTaskExecutorRegistry` (ISSUE-152).

## Critères de Done

- [ ] `AssertionExecutorRegistry`/`DefaultAssertionExecutorRegistry` supprimés
- [ ] Aucune référence restante dans le repo
- [ ] `mvn test -pl :platform-assertion -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-182 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (registry → ❌ REMOVED)
