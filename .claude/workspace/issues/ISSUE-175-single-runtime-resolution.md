# ISSUE-175 — Single Runtime Resolution incl. CLI args (T3)

**PDR** : PDR-046
**Module** : `platform-bootstrap`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Consolider les 3 chemins de résolution mode/rôle (`main().isAgentMode()`,
`RuntimeModeResolver`, `RuntimeConfigEnvironmentPostProcessor`) en un seul
`RuntimeConfigResolver` tenant compte des args CLI. Boucher le trou B5 :
`--runtime.role=AGENT` doit produire `WebApplicationType.NONE` (T3).

## Fichiers à Modifier

```
bootstrap/src/main/java/com/performance/platform/
  ├── PerformancePlatformApplication.java   — déléguer à RuntimeConfigResolver
  └── runtime/
      ├── RuntimeConfigResolver.java        — NOUVEAU (env > CLI args > property > défaut)
      ├── RuntimeConfig.java                — NOUVEAU (record)
      ├── RuntimeConfigEnvironmentPostProcessor.java — réduire/supprimer
      └── RuntimeModeResolver.java          — conserver/déléguer (ou supprimer)

bootstrap/src/test/java/com/performance/platform/runtime/
  └── RuntimeConfigResolutionTest.java      — NOUVEAU (T3)
```

## Interfaces à Implémenter

```java
public record RuntimeConfig(RuntimeMode mode, RuntimeRole role, String transportType) {}
public final class RuntimeConfigResolver {
    public RuntimeConfigResolver(Environment environment);
    public RuntimeConfig resolve(String[] cliArgs);
    public RuntimeMode resolveMode(String[] cliArgs);
    public RuntimeRole resolveRole(String[] cliArgs);
    public String resolveTransportType(String[] cliArgs);
}
```

## Règles Spécifiques

- Priorité : env var > CLI args (`--runtime.mode=`, `--runtime.role=`, `--transport.type=`) > property > défaut.
- T3 : test paramétré sur triples `(env, property, cli-arg)` ; `--runtime.role=AGENT` → `WebApplicationType.NONE`.
- `--scenario=` en AGENT reste invalide (exit 2, ADR-021).

## Critères de Done

- [ ] `RuntimeConfigResolutionTest` (T3) vert
- [ ] `--runtime.role=AGENT` → `WebApplicationType.NONE`
- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-175 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (RuntimeConfig/RuntimeConfigResolver)
