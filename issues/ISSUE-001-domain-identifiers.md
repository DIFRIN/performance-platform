# ISSUE-001 — Identifiants value objects du domaine

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : —
**Estime** : M

---

## Objectif

Créer tous les identifiants value objects immuables du domaine (records 0-framework)
avec leurs factories. Base pour tout le reste du projet.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/id/
  ├── ExecutionId.java     — id d'exécution, generate()/of()
  ├── ScenarioId.java      — id de scénario, of()
  ├── TaskId.java          — id de task dans un scénario, of()
  ├── AgentId.java         — id d'agent, generate()/of()
  ├── MessageId.java       — id de message transport, generate()/of()
  ├── EventId.java         — id d'event, generate()
  ├── SignalId.java        — id de signal, generate()
  └── ReportId.java        — id de rapport, generate()

platform-domain/src/test/java/com/performance/platform/domain/id/
  └── IdentifiersTest.java — non-null validation, égalité par valeur, factories
```

## Interfaces à Implémenter

```java
public record ExecutionId(String value) {
    public ExecutionId { Objects.requireNonNull(value, "value required"); }
    public static ExecutionId generate() { return new ExecutionId(UUID.randomUUID().toString()); }
    public static ExecutionId of(String value) { return new ExecutionId(value); }
}
public record ScenarioId(String value) {
    public ScenarioId { Objects.requireNonNull(value, "value required"); }
    public static ScenarioId of(String value) { return new ScenarioId(value); }
}
public record TaskId(String value) {
    public TaskId { Objects.requireNonNull(value, "value required"); }
    public static TaskId of(String value) { return new TaskId(value); }
}
public record AgentId(String value) {
    public AgentId { Objects.requireNonNull(value, "value required"); }
    public static AgentId generate() { return new AgentId(UUID.randomUUID().toString()); }
    public static AgentId of(String value) { return new AgentId(value); }
}
public record MessageId(String value) {
    public MessageId { Objects.requireNonNull(value, "value required"); }
    public static MessageId generate() { return new MessageId(UUID.randomUUID().toString()); }
    public static MessageId of(String value) { return new MessageId(value); }
}
public record EventId(String value) {
    public static EventId generate() { return new EventId(UUID.randomUUID().toString()); }
}
public record SignalId(String value) {
    public static SignalId generate() { return new SignalId(UUID.randomUUID().toString()); }
}
public record ReportId(String value) {
    public static ReportId generate() { return new ReportId(UUID.randomUUID().toString()); }
}
```

## Règles Spécifiques

- 0 annotation Spring/JPA/Jackson. Que du `java.util.Objects` et `java.util.UUID`.
- `value` non-null pour les ids manipulés en clé de map.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `ExecutionId.generate()` produit des valeurs uniques (UUID)
- [ ] `new ExecutionId(null)` lève `NullPointerException`
- [ ] Égalité par valeur vérifiée (record)
- [ ] `progress.md` mis à jour : ISSUE-001 → DONE
- [ ] `context/interfaces-registry.md` : ids → STABLE
