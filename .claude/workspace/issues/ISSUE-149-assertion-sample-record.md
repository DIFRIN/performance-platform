# ISSUE-149 -- AssertionSample Domain Record

**PDR** : PDR-034
**Module** : `platform-domain`
**Statut** : WAITING
**Priorite** : P1 (critique -- AssertionSummary depend de AssertionSample, mais AssertionSummary peut etre teste sans)
**Bloquee par** : ISSUE-148 (AssertionSummary reference AssertionSample)
**Estime** : S (< 1h)

---

## Objectif

Creer le record `AssertionSample` dans `platform-domain` -- un echantillon individuel collecte pendant une assertion basee sur des intervalles. Ce record est reference par `AssertionSummary.history`.

## Fichiers a Creer

```
platform-domain/src/main/java/com/performance/platform/domain/assertion/
  └── AssertionSample.java       -- record immuable: sampledAt, observedValue, unit, metadata

platform-domain/src/test/java/com/performance/platform/domain/assertion/
  └── AssertionSampleTest.java   -- tests: constructeur compact, defensive copy metadata
```

## Interfaces a Implementer

```java
// Du PDR-034, a implementer EXACTEMENT :
public record AssertionSample(
    Instant sampledAt,
    double observedValue,
    String unit,
    Map<String, Object> metadata   // extra context (e.g., topic partition offset, endpoint)
) {
    public AssertionSample {
        Objects.requireNonNull(sampledAt, "sampledAt required");
        Objects.requireNonNull(unit, "unit required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
```

## Regles Specifiques

- 0 annotation Spring/JPA/Jackson -- domaine pur
- `metadata` doit etre copie defensivement dans le constructeur compact
- Les assertions ponctuelles n'utilisent PAS ce record -- il est uniquement pour les assertions basees sur des intervalles (Phase B, future)
- La creation de ce record maintenant evite de casser la compatibilite de `AssertionSummary` plus tard

## Criteres de Done

- [ ] `mvn test -pl platform-domain -q` -> 0 erreur
- [ ] `AssertionSample` compile
- [ ] Test : metadata null -> remplace par Map.of() (pas NPE)
- [ ] Test : toutes les valeurs passes sont preservees
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-149 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : AssertionSample -> STABLE
