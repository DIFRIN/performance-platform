# PDR-046 — Single Runtime Mode/Role Resolution

**Module Maven** : `platform-app`
**Package** : `com.performance.platform.runtime`
**Statut** : WAITING
**Specs de référence** : `improvements.md` B5 ; `ADR-006-runtime-config-priority.md` ; `ADR-019-web-ui-serving-and-security.md` ; `ADR-021-headless-cli-run-mode.md`
**Dépend de** : rien
**Issues** : ISSUE-175

---

## Responsabilité

Éliminer le triple chemin de résolution mode/rôle qui a laissé passer le trou B5 :
`main().isAgentMode()`, `RuntimeModeResolver`, `RuntimeConfigEnvironmentPostProcessor`.
Un seul point de résolution, tenant compte des **arguments CLI** (`--runtime.mode=`,
`--runtime.role=`, `--transport.type=`) en plus de env et property.

> ADR requise (ADR-032) : résolution unique mode/rôle — raffine ADR-006/019/021.

---

## Interfaces Publiques

```java
package com.performance.platform.runtime;

public record RuntimeConfig(RuntimeMode mode, RuntimeRole role, String transportType) {}

/**
 * SEUL composant de résolution mode/rôle/transport.
 * Priorité : env var > CLI args > property Spring > défaut.
 */
public final class RuntimeConfigResolver {

    public RuntimeConfigResolver(Environment environment);

    public RuntimeConfig resolve(String[] cliArgs);

    public RuntimeMode resolveMode(String[] cliArgs);       // RUNTIME_MODE > --runtime.mode= > runtime.mode > LOCAL
    public RuntimeRole resolveRole(String[] cliArgs);       // MODE        > --runtime.role= > runtime.role > NONE
    public String     resolveTransportType(String[] cliArgs); // TRANSPORT_TYPE > --transport.type= > transport.type > null

    static RuntimeMode parseMode(String value);
    static RuntimeRole parseRole(String value);
}
```

`PerformancePlatformApplication.main()` délègue à `RuntimeConfigResolver.resolve(args)`
pour décider `WebApplicationType.NONE` (AGENT ou `--scenario=`) et valider les
conflits (`--scenario=` en AGENT → exit 2). `RuntimeConfigEnvironmentPostProcessor`
est supprimé ou réduit à la ré-écriture env→property (bootstrap `@ConditionalOnProperty`),
sans re-parsing de rôle.

---

## Règles de Comportement

- `--runtime.role=AGENT` DOIT produire `WebApplicationType.NONE` (T3) — c'est le
  trou B5 : aujourd'hui non détecté.
- Le parsing ne lève jamais d'exception non traduite : valeur invalide → message
  clair + exit 2 en CLI, `IllegalArgumentException` sinon.
- La résolution est testée par `RuntimeConfigResolutionTest` sur des triples
  `(env, property, cli-arg)` (T3).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-018 (RuntimeModeResolver/RuntimeMode/RuntimeRole existants)

Ce PDR est utilisé par :
  PDR-050 (consolidation) → le main reste le point d'entrée unique
```

---

## Critères de Done (PDR complet)

- [ ] Un seul chemin de résolution (ISSUE-175)
- [ ] `--runtime.role=AGENT` → `WebApplicationType.NONE` (T3)
- [ ] `RuntimeConfigResolutionTest` paramétré sur (env, property, cli-arg) passe
- [ ] `mvn test -pl platform-app -q` → 0 erreur
