# ISSUE-180 — ModuleDependencyArchTest (T1)

**PDR** : PDR-048
**Module** : `platform-bootstrap`
**Statut** : DONE
**Priorité** : P0 (ratchet qui rend la dérive récurrente)
**Bloquée par** : ISSUE-177, ISSUE-178, ISSUE-179
**Estime** : M (1-3h)

---

## Objectif

Ajouter le test inter-modules qui encode le DAG cible (T1) et échoue si une
arête interdite réapparaît. C'est le remplacement fonctionnel de la vérification
Modulith décorative (A5).

**Constat d'implémentation (2026-10-19)** : ArchUnit 1.3.0 et 1.4.0 importent
silencieusement **0 classe** sur du bytecode Java 25 (major 69) — vérifié with
`ClassFileImporter.importPath` (1 classe Java 21 importée, 0 en Java 25). Tous
les tests ArchUnit existants du repo sont donc vacuous. Le test T1 doit utiliser
un scanner ASM compatible Java 25 (l'ASM repackagé de Spring 7.0.3 fonctionne).

## Fichiers à Créer

```
bootstrap/src/test/java/com/performance/platform/arch/
  └── ModuleDependencyTest.java            — NOUVEAU (scan bytecode via Spring ASM)
```

Le test inclut une assertion de non-vacuité : il vérifie qu'il voit au moins
une classe de chaque module analysé.

## Règles Spécifiques

- Assertions du DAG (via `JavaClass`/`ClassFileImporter` ou `@AnalyzeClasses` sur tous les modules) :
  - `platform-execution-engine` ne dépend pas de `platform-assertion` / `platform-agent-runtime` / `platform-infrastructure`
  - `platform-reporting` ne dépend pas de `platform-injection-gatling`
  - `platform-assertion` ne dépend pas de `platform-infrastructure`
  - les adapters (infrastructure) ne sont jamais dépendus par les cores (domain/application)
- Le test doit passer après ISSUE-177/178/179.

## Critères de Done

- [ ] `ModuleDependencyTest` (T1) vert et non-vacuous
- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-180 → DONE
