# ISSUE-151 -- AssertionResultMapper Utility

**PDR** : PDR-036
**Module** : `platform-assertion`
**Statut** : APPROVED
**Priorite** : P1 (critique -- utilitaire partage utilise par PDR-037)
**Bloquee par** : ISSUE-148 (AssertionSummary), ISSUE-150 (AssertionExecutor extends TaskExecutor)
**Estime** : M (1-2h)

---

## Objectif

Creer la classe utilitaire `AssertionResultMapper` dans `platform-assertion`. Cette classe extrait la logique de conversion `AssertionResult -> TaskResult` hors de `DagPhaseExecutor` pour la rendre reutilisable par tous les modules (engine, agents, interface default).

## Fichiers a Creer

```
platform-assertion/src/main/java/com/performance/platform/assertion/
  └── AssertionResultMapper.java       -- utilitaire statique: toTaskResult(), extractSummary()

platform-assertion/src/test/java/com/performance/platform/assertion/
  └── AssertionResultMapperTest.java   -- tests: tous les statuts, history vide/non-vide, extractSummary
```

## Interfaces a Implementer

```java
// Du PDR-036, a implementer EXACTEMENT :
public final class AssertionResultMapper {
    private AssertionResultMapper() {}

    public static TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step) { ... }
    public static TaskResult toTaskResult(AssertionResult assertionResult, StepDefinition step, List<AssertionSample> history) { ... }
    public static AssertionSummary extractSummary(TaskResult taskResult) { ... }
}
```

> La signature complete est dans PDR-036. Implementer les 3 methodes exactement comme specifie.

## Regles Specifiques

- Classe finale, constructeur prive -- pur utilitaire, jamais instancie
- 0 annotation Spring -- classe Java pure
- `toTaskResult()` (sans history) delegue a `toTaskResult()` avec history = `List.of()`
- `extractSummary()` gere les cas : taskResult null, outputs null, cle "assertion" absente, valeur non-AssertionSummary

## Criteres de Done

- [ ] `mvn test -pl platform-assertion -q` -> 0 erreur
- [ ] `AssertionResultMapper` compile
- [ ] Test : toTaskResult avec PASSED -> TaskResult.status=SUCCESS, outputs["assertion"] est un AssertionSummary
- [ ] Test : toTaskResult avec FAILED -> TaskResult.status=FAILED, errorMessage = description
- [ ] Test : toTaskResult avec SKIPPED -> TaskResult.status=SKIPPED
- [ ] Test : toTaskResult avec ERROR -> TaskResult.status=FAILED
- [ ] Test : toTaskResult avec history non-vide -> AssertionSummary.history preserve les echantillons
- [ ] Test : extractSummary avec TaskResult ayant "assertion" -> retourne l'AssertionSummary
- [ ] Test : extractSummary avec TaskResult sans "assertion" -> retourne null
- [ ] Test : extractSummary avec TaskResult null -> retourne null
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-151 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : AssertionResultMapper -> STABLE
