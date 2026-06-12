# ISSUE-062 — KafkaAssertionExecutor (@Assertion name="kafka")

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-059
**Estime** : M

---

## Objectif

Implémenter `KafkaAssertionExecutor` : évalue une métrique Kafka (consumedCount/producedCount/lag)
en lisant le résultat d'un step KafkaConsumer du contexte.

## Fichiers à Créer

```
platform-assertion/src/main/java/com/performance/platform/assertion/kafka/
  └── KafkaAssertionExecutor.java

platform-assertion/src/test/java/com/performance/platform/assertion/kafka/
  └── KafkaAssertionExecutorTest.java
```

## Interfaces à Implémenter

```java
@Assertion(name = "kafka", description = "Kafka consumedCount/producedCount/lag assertions")
public class KafkaAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "kafka"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `metric` (consumedCount/producedCount/lag), `topic`, `groupId`, `operator`, `value`, `refTaskId`.
- Lit via `context.getFirst(refTaskId, Map.class)` (outputs du KafkaConsumerTaskExecutor).
- Comparaison via `AssertionOperator`.
- `Evidence` rempli. Erreur → `AssertionStatus.ERROR`.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → 0 erreur
- [ ] `consumedCount > N` évalué depuis le contexte
- [ ] `progress.md` mis à jour : ISSUE-062 → DONE
- [ ] `context/interfaces-registry.md` : `KafkaAssertionExecutor` → STABLE
