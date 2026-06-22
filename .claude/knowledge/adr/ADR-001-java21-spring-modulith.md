# ADR-001 — Java 25 + Spring Boot 4.x + Spring Modulith

**Date** : 2026-06-05  
**Statut** : ACCEPTED  
**Décideurs** : Équipe Architecture

---

## Contexte

Choix du runtime et du framework principal pour la plateforme.

## Décision

Java 25 (LTS) avec Spring Boot 4.x et Spring Modulith.

## Justification

- **Java 25 Virtual Threads** : permet d'exécuter 10 000+ tâches concurrentes sans
  overhead de threads natifs. Critique pour la scalabilité du moteur d'exécution.
- **Spring Boot 4.x** : requis pour la compatibilité Spring Modulith + Jakarta EE 11.
- **Spring Modulith** : enforces les frontières de modules à la compilation et au test.
  Garantit qu'aucun module ne dépend directement d'un autre (via EventVerificationTests).
- **Maven multi-module** : isolation des dépendances par module, build incrémental,
  possibilité de déployer des modules séparément si besoin futur.

## Conséquences

- Java 21 et antérieurs ne sont pas supportés.
- Spring Boot 3.x et antérieurs ne sont pas supportés.
- Les tests de modules Spring Modulith (`@ApplicationModuleTest`) sont obligatoires.
- Gatling 3.x Java DSL est requis (le Scala DSL n'est pas maintenu activement).

## Alternatives Rejetées

- **Quarkus** : meilleur démarrage, mais écosystème plus petit pour Gatling + Spring Modulith.
- **Micronaut** : idem Quarkus.
- **Kotlin** : ajouterait une couche d'apprentissage sans bénéfice architectural décisif ici.
