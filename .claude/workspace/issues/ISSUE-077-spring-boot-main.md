# ISSUE-077 — SpringBoot main + Modulith + assemblage Maven

**PDR** : PDR-018
**Module** : `platform-bootstrap`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-023, ISSUE-039, ISSUE-052
**Estime** : M

---

## Objectif

Créer le module `platform-bootstrap` (artefact unique), la classe main Spring Boot + Modulith, et
assembler les dépendances Maven de tous les modules.

## Fichiers à Créer

```
bootstrap/pom.xml — dépend de TOUS les modules ; spring-boot-maven-plugin (fat jar)
bootstrap/src/main/java/com/performance/platform/app/
  └── PerformancePlatformApplication.java

bootstrap/src/test/java/com/performance/platform/app/
  ├── ApplicationContextTest.java        — le contexte démarre
  └── ModulithVerificationTest.java      — ApplicationModules.verify()
```

## Interfaces à Implémenter

```java
@SpringBootApplication
@Modulith
public class PerformancePlatformApplication {
    public static void main(String[] args) { SpringApplication.run(PerformancePlatformApplication.class, args); }
}
```

## Règles Spécifiques

- Un seul JAR exécutable, une seule classe main (CF-01).
- `@Modulith` : pas de dépendance directe entre modules (communication via events).
- Nom du fat jar : `performance-platform.jar`.

## Critères de Done

- [ ] `mvn clean install` → `bootstrap/target/performance-platform.jar` produit
- [ ] `ApplicationModules.of(...).verify()` passe
- [ ] Le contexte Spring démarre en profil de test
- [ ] `.claude/progress.md` mis à jour : ISSUE-077 → DONE
