# ISSUE-078 — RuntimeModeResolver (env var prioritaire sur properties)

**PDR** : PDR-018
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-077
**Estime** : M

---

## Objectif

Implémenter la résolution du mode/rôle/transport avec la priorité env var > property (ADR-006),
réécrite en properties Spring au bootstrap pour piloter les `@ConditionalOnProperty`.

## Fichiers à Créer

```
platform-app/src/main/java/com/performance/platform/app/runtime/
  ├── RuntimeModeResolver.java
  ├── RuntimeRole.java
  └── RuntimeConfigEnvironmentPostProcessor.java   — applique la priorité env var

platform-app/src/test/java/com/performance/platform/app/runtime/
  └── RuntimeModeResolverTest.java — env var gagne, fallback property
```

## Interfaces à Implémenter

```java
public enum RuntimeRole { ORCHESTRATOR, AGENT, NONE }

@Component
public class RuntimeModeResolver {
    public RuntimeMode resolveMode();   // RUNTIME_MODE env > runtime.mode yaml
    public RuntimeRole resolveRole();   // MODE env > runtime.role yaml
}
```

## Règles Spécifiques

- Priorité (ADR-006) : `RUNTIME_MODE` > `runtime.mode` ; `MODE` > `runtime.role` ; `TRANSPORT_TYPE` > `transport.type`.
- **Env var gagne TOUJOURS**, même si une property est définie.
- `EnvironmentPostProcessor` réécrit les properties depuis les env vars avant la création des beans (pour `@ConditionalOnProperty`).

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `RUNTIME_MODE=DISTRIBUTED` override `runtime.mode: LOCAL` du yaml
- [ ] `.claude/progress.md` mis à jour : ISSUE-078 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `RuntimeModeResolver` → STABLE
