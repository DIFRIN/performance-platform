# Skill — Testing Strategy

---

## Pyramide de Tests

```
        /\
       /  \
      / E2E \          (1-3 scénarios complets bout-en-bout)
     /────────\
    /Integration\      (Testcontainers : DB, Kafka, WireMock)
   /────────────\
  / Unit Tests   \     (Domaine + Use Cases : rapides, sans I/O)
 /────────────────\
```

---

## Tests Unitaires (platform-domain, platform-application)

- Pas de Spring, pas de Mockito si possible (domain pur)
- Records immuables → faciles à construire dans les tests
- Coverage cible : 90%

```java
class ExecutionContextTest {
    @Test
    void shouldBeImmutableOnWith() {
        var ctx = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s1"));
        var ctx2 = ctx.with("key", "value");
        assertThat(ctx.store()).doesNotContainKey("key");
        assertThat(ctx2.store()).containsEntry("key", "value");
    }
}
```

---

## Tests d'Intégration (platform-infrastructure)

Utiliser Testcontainers. Ne PAS mocker la DB ou Kafka.

```java
@SpringBootTest
@Testcontainers
class DatabaseTaskExecutorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }

    @Test
    void shouldPurgeTable() { ... }
}
```

---

## Tests Spring Modulith

```java
@ApplicationModuleTest
class ExecutionModuleTest {
    @Test
    void shouldPublishScenarioStartedEvent() {
        // Vérifie que le module publie bien l'event sans dépendance directe sur Reporting
    }
}
```

---

## Conventions de Nommage

- Unit test : `ClassNameTest`
- Integration test : `ClassNameIT` (suffixe IT déclenche le profil `integration-tests`)
- Fixtures : dans `src/test/resources/fixtures/`
- Scénarios YAML de test : `src/test/resources/scenarios/`

---

## Coverage

```xml
<!-- pom.xml parent : Jacoco configuré avec seuils -->
<minimum>
  <package>com.performance.platform.domain.*</package>
  <line>0.90</line>
  <application>com.performance.platform.application.*</application>
  <line>0.80</line>
</minimum>
```
