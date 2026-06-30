# ISSUE-141 — Garde Docker sur DatabaseAssertionExecutorIT + @TempDir dans FileAssertionExecutorTest

**PDR** : PDR-032
**Module** : `platform-assertion`
**Statut** : DONE
**Priorité** : P1 (critique)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

1. Ajouter `@Testcontainers(disabledWithoutDocker = true)` à `DatabaseAssertionExecutorIT`.
2. Corriger `FileAssertionExecutorTest` : remplacer le littéral `/tmp` (+ check `EXISTS`) par
   un répertoire `@TempDir` portable (ADR-023).

## Fichiers à Créer / Modifier

```
MODIFIER (tests) :
platform-assertion/src/test/java/.../DatabaseAssertionExecutorIT.java
  — @Testcontainers(disabledWithoutDocker = true)
platform-assertion/src/test/java/.../FileAssertionExecutorTest.java
  — "/tmp" littéral → @TempDir Path tempDir (chemin réel portable Windows/macOS/Linux)
```

## Interfaces à Implémenter

```java
// IT Docker
@Testcontainers(disabledWithoutDocker = true)
class DatabaseAssertionExecutorIT { ... }

// Test fichier portable
@TempDir Path tempDir;

@Test
void shouldPassWhenFileExists() throws IOException {
    Path file = Files.createFile(tempDir.resolve("evidence.txt"));
    // assertion EXISTS sur file.toString() — plus de "/tmp"
}
```

## Règles Spécifiques

- `DatabaseAssertionExecutorIT` : ajout du paramètre `disabledWithoutDocker = true` uniquement.
- `FileAssertionExecutorTest` : tout chemin testé en I/O réelle passe par `@TempDir`. Aucun
  littéral `/tmp`, `/var/...`, ni séparateur POSIX en dur. Utiliser `Path`/`Files`.
- Si d'autres tests du module utilisent un `/tmp` réel (I/O), les corriger aussi ; les
  `Path.of("/tmp/...")` purement en mémoire (jamais ouverts) peuvent rester mais privilégier
  une valeur neutre si on y touche.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → vert sur tout OS (sans Docker : l'IT `skipped`)
- [ ] `FileAssertionExecutorTest` : plus aucun littéral `/tmp` ; utilise `@TempDir`
- [ ] `DatabaseAssertionExecutorIT` porte `@Testcontainers(disabledWithoutDocker = true)`
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
</content>
