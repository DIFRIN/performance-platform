# ADR-035 — Retrait de Spring Modulith et enforcement des frontières par scan bytecode ASM

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A5)
**Raffine** : ADR-001 (Spring Modulith), PDR-048/PDR-049
**Impacte interfaces publiques** : NON (retrait d'une annotation et d'un test)

---

## Contexte

Deux problèmes distincts ont été vérifiés :

1. `ModulithVerificationTest` n'appelait jamais `modules.verify()` : il vérifiait
   seulement que des modules étaient détectés. De plus, Spring Modulith analyse la
   structure de packages d'**un seul artefact** : avec 14 modules Maven, il ne voit
   pas les arêtes inter-modules.
2. **ArchUnit 1.3.0 et 1.4.0 importent silencieusement 0 classe sur du bytecode
   Java 25 (major 69).** Vérification : un répertoire contenant une classe
   compilée `--release 21` donne `importPath(...).size() == 1` ; la même classe
   compilée en Java 25 donne `0`. Spring Modulith s'appuyant sur ArchUnit, même
   `modules.verify()` serait vide. Tous les tests ArchUnit existants du repo sont
   donc vacuous.

## Décision

- Retirer `@Modulith`, `ModulithVerificationTest` et les dépendances
  `spring-modulith-starter-core` / `spring-modulith-starter-test` du module app.
- Enforcer les frontières inter-modules avec `ModuleDependencyTest`
  (`platform-app/src/test/java/.../arch`) :
  - lecture des `.class` via l'ASM repackage de Spring 7 (compatible Java 25) ;
  - vérification des arêtes interdites : engine ↛ assertion, engine ↛ agent,
    reporting ↛ injection, assertion ↛ injection, assertion ↛ infrastructure
    (hors `HttpMockAssertionExecutor`, exception documentée) ;
  - test de non-vacuité : chaque module analysé doit fournir au moins une classe.
- Le scan exclut les classes de test et privilégie `target/classes` aux jars
  installés.

## Conséquences

- L'enforcement est réel et échoue si une arête interdite réapparaît.
- La dépendance `spring-modulith-bom` reste dans le BOM (aucun coût).
- Les tests ArchUnit des autres modules restent vacuous sur Java 25 : leur
  migration vers le scanner ASM est un suivi (hors périmètre de cet ADR).

## Preuves

- `ModuleDependencyTest` : 6 tests verts, dont le test de non-vacuité.
- `mvn -pl platform-app clean test` vert après retrait de Modulith.
