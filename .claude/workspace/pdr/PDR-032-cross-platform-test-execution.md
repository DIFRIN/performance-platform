# PDR-032 — Cross-Platform Test Execution

**Module Maven** : `platform-infrastructure`, `platform-transport`, `platform-assertion`
**Package** : tests uniquement (`src/test/java`)
**Statut** : WAITING
**Specs de référence** : ADR-023 (cross-platform test execution — Docker + shell + paths +
encodage), skill testing-strategy.md, constraints.md CC-01
**Dépend de** : rien (peut démarrer indépendamment ; idéalement après PDR-030 pour un build sain)
**Issues** : ISSUE-139, ISSUE-140, ISSUE-141, ISSUE-142

---

## Responsabilité

Rendre la suite de tests **verte sur Windows / macOS / Linux sans Docker** (`mvn test`), sans
toucher au code de production. Trois leviers (ADR-023) :

1. **Docker** : les 7 `*IT.java` Testcontainers portent `@Testcontainers(disabledWithoutDocker = true)`
   → **skipped** (pas en erreur) si Docker absent.
2. **Shell Unix** : les tests qui lancent un process shell/POSIX (`bash`, `echo`, `touch`,
   `sleep`, `pwd`) portent `@DisabledOnOs(OS.WINDOWS)`.
3. **Paths & encodage** : chemins de test filesystem via `@TempDir` (jamais `/tmp` littéral) ;
   conversions texte en `StandardCharsets.UTF_8` explicite (production déjà conforme).

Ce qu'il ne fait PAS : ne modifie **aucun** code de production (`ShellTaskExecutor`,
`FilesystemTaskExecutor`, renderers déjà conformes). Ne change pas la stratégie de CI (les IT
restent exécutées avec Docker via `-P integration-tests`).

---

## Interfaces Publiques

> Aucune interface Java. Patterns de test canoniques (ADR-023) :

```java
// 1. IT Testcontainers — garde Docker au niveau classe
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class XxxIT { /* @Container ... */ }

// 2. Test dépendant d'un shell Unix — garde OS
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
@Test
void shouldRunBashCommand() { ... }

// 3. Chemin filesystem portable
@TempDir Path tempDir;   // au lieu de Path.of("/tmp")
```

---

## Règles de Comportement

- **7 ITs concernés** (ajout `disabledWithoutDocker = true`) :
  - `platform-infrastructure` : `DatabaseTaskExecutorIT`, `KafkaTaskExecutorsIT`,
    `EntitiesMappingIT`, `JpaExecutionRepositoryIT`
  - `platform-transport` : `KafkaExecutionTransportIT`, `RabbitMQExecutionTransportIT`
  - `platform-assertion` : `DatabaseAssertionExecutorIT`
- **`ShellTaskExecutorTest`** (`platform-infrastructure`) : `@DisabledOnOs(OS.WINDOWS)` sur les
  tests/`@Nested` qui exécutent réellement un process shell (`bash`/`echo`/`touch`/`sleep`/`pwd`,
  redirections). Les tests **purement logiques** (command manquant/blanc, timeout négatif, NPE
  contexte/step null, `getSupportedTaskName`) **restent exécutés partout** (pas de garde).
  `workingDirectory = "/tmp"` → `@TempDir`.
- **`FileAssertionExecutorTest`** (`platform-assertion`) : `"/tmp"` + check `EXISTS` → `@TempDir`.
- **Encodage** : si un test/main non conforme (`new String(bytes)` / `getBytes()` sans charset,
  `Charset.defaultCharset()`) est rencontré dans le périmètre touché, le corriger en
  `StandardCharsets.UTF_8`. État connu : production déjà conforme — pas de chasse exhaustive
  hors périmètre.
- **Ne pas utiliser** `Assumptions.assumeTrue(dockerAvailable)` (ADR-023 : préférer
  `disabledWithoutDocker` au niveau classe).
- **Vérification** : `mvn test` (sans Docker) doit être **vert** dans chaque module ; les IT
  apparaissent `skipped`, jamais `error`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  Testcontainers JUnit5 (déjà dépendance test des 3 modules)
  JUnit 5 @DisabledOnOs / @TempDir (déjà présents)

Ce PDR est utilisé par :
  CI / postes de dev sans Docker (build vert)
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE (ISSUE-139..142)
- [ ] `mvn test` (sans Docker) vert sur `platform-infrastructure`, `platform-transport`,
      `platform-assertion` — les 7 IT `skipped`
- [ ] `ShellTaskExecutorTest` : tests shell `skipped` sur Windows, exécutés sur Linux/macOS ;
      plus aucun `/tmp` littéral
- [ ] `FileAssertionExecutorTest` : plus de `/tmp` littéral (`@TempDir`)
- [ ] `.claude/workspace/interfaces-registry.md` : note « tests cross-platform (ADR-023) »
</content>
