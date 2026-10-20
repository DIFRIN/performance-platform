# ISSUE-183 — Drop Decorative @Modulith + ModulithVerificationTest

**PDR** : PDR-049
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P3
**Bloquée par** : ISSUE-180
**Estime** : S (< 1h)

---

## Objectif

Retirer la vérification Spring Modulith décorative (A5) : `ModulithVerificationTest`
n'appelle jamais `modules.verify()`. L'annotation `@Modulith` et le test sont
supprimés ; `ModuleDependencyTest` (T1) devient l'unique enforcement.

> ADR requise (ADR-035).

## Fichiers à Modifier

```
platform-app/src/main/java/com/performance/platform/PerformancePlatformApplication.java — retirer @Modulith
platform-app/src/test/java/com/performance/platform/app/ModulithVerificationTest.java    — SUPPRIMER
```

## Règles Spécifiques

- Aucun changement de comportement runtime.
- La dépendance `spring-modulith-bom` peut rester dans le root pom (aucun coût).

## Critères de Done

- [ ] `@Modulith` retiré + `ModulithVerificationTest` supprimé
- [ ] `ModuleDependencyTest` reste vert
- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-183 → DONE
