# ISSUE-064 — FileAssertionExecutor (@Assertion name="file")

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-059
**Estime** : S

---

## Objectif

Implémenter `FileAssertionExecutor` : vérifie l'existence/checksum/taille d'un fichier.

## Fichiers à Créer

```
platform-assertion/src/main/java/com/performance/platform/assertion/file/
  └── FileAssertionExecutor.java

platform-assertion/src/test/java/com/performance/platform/assertion/file/
  └── FileAssertionExecutorTest.java — EXISTS/CHECKSUM/SIZE sur @TempDir
```

## Interfaces à Implémenter

```java
@Assertion(name = "file", description = "File EXISTS/CHECKSUM/SIZE assertions")
public class FileAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "file"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `path`, `check` (EXISTS/NOT_EXISTS/CHECKSUM/SIZE_GT/SIZE_LT), `checksum` (sha256:...), `sizeBytes`.
- `Evidence` : actual=état réel, expected=attendu.
- Fichier absent pour CHECKSUM/SIZE → `AssertionStatus.ERROR` ou FAILED selon check.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → 0 erreur
- [ ] EXISTS et CHECKSUM sha256 vérifiés sur `@TempDir`
- [ ] `.claude/progress.md` mis à jour : ISSUE-064 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `FileAssertionExecutor` → STABLE
