# ISSUE-142 — Rendre ShellTaskExecutorTest cross-platform

**PDR** : PDR-032
**Module** : `platform-infrastructure`
**Statut** : DONE
**Priorité** : P1 (critique)
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Rendre `ShellTaskExecutorTest` exécutable sans erreur sur Windows : marquer
`@DisabledOnOs(OS.WINDOWS)` les tests qui lancent réellement un process shell/POSIX
(`bash`/`echo`/`touch`/`sleep`/`pwd`, redirections), et remplacer le `workingDirectory = "/tmp"`
par `@TempDir`. Les tests purement logiques restent exécutés partout. Le code de production
`ShellTaskExecutor` **n'est pas modifié** (ADR-023).

## Fichiers à Créer / Modifier

```
MODIFIER (test) :
platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/shell/ShellTaskExecutorTest.java
  — @DisabledOnOs(OS.WINDOWS) sur les @Nested / @Test exécutant un process shell :
      BasicCommands (echo/bash), WorkingDirectory (pwd/bash + /tmp), EnvironmentVariables (bash),
      Timeout (bash sleep), Cleanup (bash touch+sleep), OutputsStructure (echo/bash),
      ErrorCases : shouldFailWhenCommandCannotBeExecuted (/nonexistent binary — comportement OK
      partout mais message dépend de l'OS → garder Windows-safe ou guarder selon résultat réel)
  — WorkingDirectory : "/tmp" → @TempDir Path tempDir (tempDir.toString())
  — CONSERVER sans garde (logique pure, portable) : command manquant, command blanc,
      timeout négatif/zéro, NPE contexte null, NPE step null, getSupportedTaskName
```

## Interfaces à Implémenter

```java
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

@Nested
@DisplayName("Basic shell commands")
@DisabledOnOs(OS.WINDOWS)        // dépend de bash / echo — non portable Windows
class BasicCommands { ... }

@Nested
@DisplayName("Working directory")
@DisabledOnOs(OS.WINDOWS)
class WorkingDirectory {
    @TempDir Path tempDir;        // au lieu de "/tmp"
    // workingDirectory = tempDir.toString() ; assertion stdout contains tempDir nom
}
```

## Règles Spécifiques

- **Granularité de la garde** : préférer `@DisabledOnOs(OS.WINDOWS)` au niveau `@Nested` quand
  tout le groupe lance un shell. Les tests de validation pure (sans process) restent **hors
  garde** pour conserver de la couverture sur Windows.
- **`workingDirectory`** : remplacer `/tmp` par `@TempDir`. Le test « pwd » asserte que stdout
  contient le chemin du `@TempDir` (et non `/tmp`).
- **Ne pas** modifier `ShellTaskExecutor` (production). Ne pas changer la sémantique des tests
  (mêmes assertions de statut/outputs), seulement leur portabilité.
- Conserver l'usage de `@TempDir`/`Files.createTempFile` déjà présent dans `Cleanup` (portable),
  mais garder ces tests `@DisabledOnOs(OS.WINDOWS)` car ils lancent `bash`.
- Encodage : si une lecture de sortie process est faite côté test sans charset explicite, la
  corriger en `StandardCharsets.UTF_8` (production déjà conforme).

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` vert sur Linux/macOS (tests shell exécutés)
- [ ] Sur Windows : les tests dépendant de bash sont `skipped`, les tests logiques `passent` ;
      `mvn test` vert (vérifiable à défaut par revue : aucune commande bash/`/tmp` hors garde)
- [ ] Plus aucun littéral `/tmp` dans `ShellTaskExecutorTest` (→ `@TempDir`)
- [ ] `ShellTaskExecutor` (production) inchangé
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
</content>
