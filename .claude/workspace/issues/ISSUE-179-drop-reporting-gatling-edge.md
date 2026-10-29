# ISSUE-179 — Drop reporting → injection-gatling pom Dependency

**PDR** : PDR-048
**Module** : `platform-reporting`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Supprimer la dépendance pom `reporting → injection-gatling` (A3 corrigé) : aucun
import main-code (reporting utilise `domain.injection`), c'est une arête de
compilation inutile qui force Gatling sur le classpath d'un rapport.

## Fichiers à Modifier

```
infra/platform-report-renderers/pom.xml     — retirer la dépendance platform-injection-gatling
```

## Règles Spécifiques

- Vérifier qu'aucun import `com.performance.platform.gatling` / injection-gatling
  ne subsiste dans `infra/platform-report-renderers/src/main` (la review dit zéro).
- Si un import de test subsiste, le passer en scope `test`.

## Critères de Done

- [ ] `infra/platform-report-renderers/pom.xml` ne référence plus `platform-injection-gatling`
- [ ] `mvn test -pl :platform-reporting -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-179 → DONE
