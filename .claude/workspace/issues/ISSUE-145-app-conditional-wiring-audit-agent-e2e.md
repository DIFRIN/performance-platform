# ISSUE-145 — platform-infrastructure : datasource/JPA/repository conditionnels (sans throw)

**PDR** : PDR-033
**Module** : `platform-infrastructure`
**Statut** : APPROVED
**Priorité** : P1 (critique — AGENT ne démarre pas tant que l'infra DB est inconditionnelle)
**Bloquée par** : ISSUE-134 (classpath Spring cohérent via BOM)
**Estime** : M (1-3h)

---

## Objectif

Rendre les beans d'infrastructure DB **conditionnels à la présence d'une datasource**, et
**supprimer l'`IllegalStateException`** levée par `DatasourceConfiguration` quand aucune
datasource « default » n'est configurée. Objectif : qu'un contexte sans DB (mode **AGENT**,
`platform.datasources: {}`) démarre proprement, sans bean DataSource/JPA/repository. Aligner
aussi les registries Kafka/HTTP sur le modèle conditionnel.

## Fichiers à Créer / Modifier

```
MODIFIER (main) :
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/database/DatasourceConfiguration.java
  — dataSource() : @ConditionalOnProperty(prefix="platform.datasources.default", name="url")
                   supprimer le throw IllegalStateException
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/config/JpaConfiguration.java
  — @ConditionalOnBean(DataSource.class) au niveau classe
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/JpaExecutionRepository.java
  — @ConditionalOnBean(DataSource.class) (ou condition équivalente) pour ne pas s'activer sans DB
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/kafka/KafkaClusterConfiguration.java
  — kafkaClusterRegistry : @ConditionalOnProperty(prefix="platform.kafka-clusters", matchIfMissing=true)
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/http/HttpTargetConfiguration.java
  — httpTargetRegistry : alignement conditionnel (matchIfMissing=true)

MODIFIER (test) :
platform-infrastructure/src/test/java/.../*IT.java JPA/datasource
  — activent la datasource par properties (@DynamicPropertySource Testcontainers / @TestPropertySource) ;
    supprimer tout @Bean DataSource de test dupliqué
```

## Interfaces à Implémenter

```java
// DatasourceConfiguration
@Bean
@Primary
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public DataSource dataSource(PlatformDatasourcesProperties props) {
    var ds = props.datasources().get("default");
    log.info("action=primary_datasource url={}", ds.url());
    return buildHikari("default", ds);
}
// datasourceProvider(...) : INCHANGÉ (créé même vide)

// JpaConfiguration
@Configuration
@ConditionalOnBean(DataSource.class)
@EnableJpaRepositories(basePackages = "com.performance.platform.infrastructure.persistence")
@EnableTransactionManagement
public class JpaConfiguration { /* EMF + transactionManager */ }

// JpaExecutionRepository
@Repository
@ConditionalOnBean(DataSource.class)
public class JpaExecutionRepository implements ExecutionRepository { ... }

// KafkaClusterConfiguration
@Bean
@ConditionalOnProperty(prefix = "platform.kafka-clusters", name = "...", matchIfMissing = true)
public KafkaClusterRegistry kafkaClusterRegistry(PlatformKafkaProperties props) { ... }
```

## Règles Spécifiques

- **Supprimer le `throw`** dans `dataSource()` : la condition garantit
  `platform.datasources.default.url`. Absence ⇒ pas de bean (log `info`), pas d'exception.
- **`@ConditionalOnBean(DataSource.class)`** sur `JpaConfiguration` ET `JpaExecutionRepository` :
  en AGENT (pas de datasource), aucun n'est créé → contexte démarre sans `ExecutionRepository`.
  Attention à l'ordre d'évaluation : si nécessaire, utiliser
  `@ConditionalOnProperty(prefix="platform.datasources.default", name="url")` comme condition
  équivalente sur `JpaConfiguration`/`JpaExecutionRepository` (documenter le choix).
- **`@ConditionalOnProperty`/`@ConditionalOnBean` uniquement** — pas de `@Profile`.
- **Registries Kafka/HTTP** : `matchIfMissing = true` admis (registry vide = défaut sûr,
  parité avec l'existant).
- **ITs** : activer la datasource via properties (Testcontainers `@DynamicPropertySource`
  mappant `platform.datasources.default.url` → `postgres.getJdbcUrl()`), **sans** redéfinir de
  `@Bean DataSource` de test. Vérifier qu'un contexte sans datasource ne crée aucun EMF.
- Le smoke test « AGENT démarre sans bean DB » est porté par **ISSUE-144** (platform-app) ;
  cette Issue garantit côté infra que c'est possible.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` (sans Docker) → vert ; un contexte sans
      datasource ne crée ni `DataSource`, ni EMF, ni `JpaExecutionRepository`, **sans
      `IllegalStateException`**
- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` (avec Docker) → ITs JPA/DB
      verts (datasource activée par properties)
- [ ] `dataSource()` `@ConditionalOnProperty(prefix="platform.datasources.default", name="url")`,
      plus aucun `throw` ; `JpaConfiguration` + `JpaExecutionRepository` conditionnels
- [ ] Registries Kafka/HTTP alignés sur le modèle conditionnel
- [ ] Aucun `@Bean DataSource` dupliqué dans les tests
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : note « datasource/JPA/repository conditionnels (ADR-025) »
</content>
