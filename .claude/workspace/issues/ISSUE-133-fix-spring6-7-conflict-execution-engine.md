# ISSUE-133 — Résoudre le conflit Spring 6/7 dans platform-execution-engine

**PDR** : PDR-030
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P0 (bloquant — conflit de versions latent dangereux)
**Bloquée par** : ISSUE-132
**Estime** : S (< 1h)

---

## Objectif

Retirer du `platform-execution-engine/pom.xml` les versions Spring explicites qui créent un
classpath incohérent : `spring-context 6.2.6` (**Spring 6**) mélangé à
`spring-boot-autoconfigure 4.0.0` (Spring Boot 4 = **Spring 7**). Après retrait, ces deux
artefacts héritent du BOM → Spring 7.x cohérent avec Spring Boot 4.

## Fichiers à Créer / Modifier

```
platform-execution-engine/pom.xml
  └── MODIF : retirer <version>6.2.6</version> de spring-context
             retirer <version>4.0.0</version> de spring-boot-autoconfigure
             (les artefacts restent déclarés, sans version → BOM)
```

## Interfaces à Implémenter

> Avant / après (extrait pom) :

```xml
<!-- AVANT -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>6.2.6</version>          <!-- Spring 6 — À RETIRER -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <version>4.0.0</version>          <!-- À RETIRER -->
</dependency>

<!-- APRÈS -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>   <!-- version héritée du BOM (Spring 7) -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>   <!-- version héritée du BOM -->
</dependency>
```

## Règles Spécifiques

- Ne pas retirer les autres dépendances du module (slf4j-api, mockito-core, assertj,
  slf4j-simple, platform-* internes) — elles ne sont pas Spring/Boot ou conservent leur version.
- Vérifier `mvn dependency:tree -pl platform-execution-engine` : `spring-context` doit
  remonter en version Spring 7.x (alignée Spring Boot 4), **plus aucune trace de 6.2.6**.
- Le code Java du module ne change pas (les API Spring 7 utilisées par les engines doivent
  compiler — `@Service`, `ApplicationEventPublisher`, `@ConditionalOnProperty`).

## Critères de Done

- [ ] `mvn clean install -pl platform-execution-engine -am -q` → 0 erreur
- [ ] `mvn dependency:tree -pl platform-execution-engine | grep spring-context` → version 7.x,
      **aucune** occurrence de `6.2.6`
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si nécessaire
</content>
