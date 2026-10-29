# PDR-049 — Spring Modulith Decision

**Module Maven** : `platform-bootstrap` (retrait annotation + test)
**Package** : `com.performance.platform` / `com.performance.platform.app.arch`
**Statut** : WAITING
**Specs de référence** : `improvements.md` A5 ; `ADR-001-java21-spring-modulith.md`
**Dépend de** : PDR-048 (ISSUE-180 — l'ArchUnit inter-modules remplace la vérification)
**Issues** : ISSUE-183

---

## Responsabilité

Spring Modulith est décoratif (A5) : `ModulithVerificationTest` n'appelle jamais
`modules.verify()`, et Modulith opère sur la structure de packages d'un seul
artefact — il ne voit pas les 14 modules Maven. La règle « modules = events
uniquement » est donc appliquée par rien.

Décision (ADR-035) : **retirer** l'annotation `@Modulith`, `ModulithVerificationTest`
et les dépendances `spring-modulith-starter-*`, et faire de
`ModuleDependencyTest` (T1) l'unique enforcement des frontières inter-modules.

**Constat supplémentaire (2026-10-19)** : Spring Modulith s'appuie sur ArchUnit.
ArchUnit 1.3/1.4 importe silencieusement 0 classe sur du bytecode Java 25 ; même
`modules.verify()` serait donc vide. Le test T1 utilise un scanner ASM
compatible Java 25.

> Alternative (si l'Architect préfère la consolidation mono-artefact) : garder
> Modulith et regrouper les modules — rejetée ici car contraire au multi-module
> Maven existant et hors périmètre du programme.

---

## Interfaces Publiques

Aucune. Retrait de `@Modulith` (annotation sur `PerformancePlatformApplication`)
et suppression de `ModulithVerificationTest`. La dépendance `spring-modulith`
peut rester dans le BOM (aucun coût) mais plus aucun usage runtime/verification.

---

## Règles de Comportement

- La garantie « communication inter-modules via events » est portée par
  `ModuleDependencyTest` (ISSUE-180), pas par Modulith.
- Aucune régression de comportement runtime : `@Modulith` n'influence pas
  l'exécution, seulement la vérification de démarrage.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-048 (ModuleDependencyTest)

Ce PDR est utilisé par :
  (rien)
```

---

## Critères de Done (PDR complet)

- [ ] `@Modulith` retiré (ISSUE-183)
- [ ] `ModulithVerificationTest` supprimé (ISSUE-183)
- [ ] `ModuleDependencyTest` reste le gardien des frontières
- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
