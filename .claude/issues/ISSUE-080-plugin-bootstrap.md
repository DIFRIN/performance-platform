# ISSUE-080 — PluginBootstrap (chargement au démarrage)

**PDR** : PDR-018
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-077, ISSUE-047
**Estime** : S

---

## Objectif

Implémenter `PluginBootstrap` (ApplicationRunner) qui charge les plugins une seule fois au
démarrage via `PluginLoader`, sans crasher sur JAR invalide.

## Fichiers à Créer

```
platform-app/src/main/java/com/performance/platform/app/plugin/
  ├── PluginBootstrap.java
  └── PluginProperties.java   — dir, enabled

platform-app/src/test/java/com/performance/platform/app/plugin/
  └── PluginBootstrapTest.java
```

## Interfaces à Implémenter

```java
@Component
public class PluginBootstrap implements ApplicationRunner {
    public PluginBootstrap(PluginLoader loader, PluginProperties props) { /* ... */ }
    public void run(ApplicationArguments args) { /* loader.load(props.dir()) si enabled */ }
}

@ConfigurationProperties(prefix = "platform.plugins")
public record PluginProperties(String dir, boolean enabled) {}
```

## Règles Spécifiques

- Chargement au démarrage uniquement (CF-06).
- `enabled=false` → skip.
- JAR invalide → loggé WARN, pas de crash.
- Log INFO du `PluginLoadResult` (jarsLoaded, executorsRegistered, warnings/errors).

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] Bootstrap appelle `loader.load` une fois si enabled
- [ ] `enabled=false` → loader non appelé
- [ ] `.claude/progress.md` mis à jour : ISSUE-080 → DONE
