# ISSUE-176 — DSL Strictness + Version + Line-Numbered Errors (T7)

**PDR** : PDR-047
**Module** : `platform-scenario-dsl`
**Statut** : IN_PROGRESS
**Priorité** : P1
**Bloquée par** : —
**Estime** : L (3-6h)

---

## Objectif

Rendre le parser YAML strict (B6) : `FAIL_ON_UNKNOWN_PROPERTIES=true`, `scenario.version`
requis, erreurs avec numéro de ligne. Ajouter `ScenarioStrictnessTest` (T7).

## Fichiers à Modifier

```
platform-scenario-dsl/src/main/java/com/performance/platform/scenario/parser/
  ├── YamlScenarioParser.java               — FAIL_ON_UNKNOWN_PROPERTIES=true + version requise + lignes
  └── (pas de nouvelle interface publique)

platform-scenario-dsl/src/test/java/com/performance/platform/scenario/parser/
  └── ScenarioStrictnessTest.java           — NOUVEAU (T7)

platform-scenario-dsl/src/test/resources/
  └── (corpus YAML : clé inconnue, typo, mauvais type, champ requis manquant)
```

## Règles Spécifiques

- Clé inconnue / typo / mauvais type / `version` manquant → rejet avec `ligne N : message`.
- Migrer les scénarios d'exemple existants sans `version`.
- `ScenarioValidator` reste la source de validation sémantique (pas de duplication).

## Critères de Done

- [ ] `ScenarioStrictnessTest` (T7) vert — chaque cas rejeté avec la ligne fautive
- [ ] `FAIL_ON_UNKNOWN_PROPERTIES=true` + `version` requis
- [ ] Scénarios d'exemple migrés
- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-176 → DONE
