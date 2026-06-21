# ISSUE-096 — SUT iot-dispatcher (Spring Boot : Kafka → DB → HTTP)

**PDR** : PDR-023
**Module** : `platform-examples/iot-dispatcher/` (standalone — hors Maven multi-module)
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-098 (DB schema doit être DONE — les services partagent le même schéma)
**Estime** : L

---

## Objectif

Créer le premier service SUT (System Under Test) : un microservice Spring Boot 3.4.x qui :
1. Consomme des commandes depuis un topic Kafka (`iot-commands`)
2. Pour chaque message, vérifie que `device_id` est connu en base (table `devices`)
3. Récupère le `device_dns` du device
4. Envoie la commande en HTTP POST vers `http://{device_gateway_url}/command`

Ce service est délibérément simple (pas de couche service abstraite, pas de JPA) — il doit être lisible et servir d'exemple réaliste de SUT pour la plateforme de test de performance.

---

## Fichiers à Créer

```
platform-examples/iot-dispatcher/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/performance/examples/iot/
    │   │   ├── IotDispatcherApplication.java
    │   │   ├── DeviceCommandConsumer.java
    │   │   ├── DeviceRepository.java
    │   │   └── IotHttpClient.java
    │   └── resources/
    │       └── application.yaml
    └── test/
        └── java/com/performance/examples/iot/
            └── DeviceCommandConsumerTest.java
```

---

## Code à Implémenter

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
</parent>
<dependencies>
    <dependency>org.springframework.boot:spring-boot-starter</dependency>
    <dependency>org.springframework.kafka:spring-kafka</dependency>
    <dependency>org.springframework.boot:spring-boot-starter-jdbc</dependency>
    <dependency>org.springframework.boot:spring-boot-starter-web</dependency>
    <dependency>org.postgresql:postgresql:42.7.3</dependency>
    <!-- test -->
    <dependency>org.springframework.boot:spring-boot-starter-test:test</dependency>
    <dependency>org.springframework.kafka:spring-kafka-test:test</dependency>
</dependencies>
```

```java
// IotDispatcherApplication.java
@SpringBootApplication
public class IotDispatcherApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotDispatcherApplication.class, args);
    }
}

// DeviceCommandConsumer.java
@Component
public class DeviceCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandConsumer.class);

    private final DeviceRepository deviceRepository;
    private final IotHttpClient iotHttpClient;

    public DeviceCommandConsumer(DeviceRepository deviceRepository, IotHttpClient iotHttpClient) {
        this.deviceRepository = deviceRepository;
        this.iotHttpClient = iotHttpClient;
    }

    @KafkaListener(
        topics     = "${iot.topics.commands:iot-commands}",
        groupId    = "${iot.kafka.group-id:iot-dispatcher}",
        concurrency = "${iot.kafka.concurrency:3}"
    )
    public void handle(String message) {
        try {
            // 1. Parser le message JSON minimal : {"device_id":"xxx","command":"ping"}
            String deviceId = extractDeviceId(message);
            if (deviceId == null) {
                log.warn("action=skip_message reason=no_device_id message={}", message);
                return;
            }

            // 2. Lookup en DB
            Optional<String> dns = deviceRepository.findDnsByDeviceId(deviceId);
            if (dns.isEmpty()) {
                log.warn("action=skip_message reason=unknown_device device_id={}", deviceId);
                return;
            }

            // 3. Dispatch HTTP
            int status = iotHttpClient.sendCommand(dns.get(), message);
            log.info("action=command_dispatched device_id={} dns={} status={}", deviceId, dns.get(), status);

        } catch (Exception e) {
            log.error("action=dispatch_error message={}", message, e);
            // Pas de rethrow — le message est "acked" (pas de retry Kafka infini)
        }
    }

    private String extractDeviceId(String json) {
        // Extraction JSON minimaliste (pas de lib JSON pour garder la dépendance légère)
        // Pattern : "device_id":"value"
        int idx = json.indexOf("\"device_id\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1) + 1;
        int end   = json.indexOf('"', start);
        return start > 0 && end > start ? json.substring(start, end) : null;
    }
}

// DeviceRepository.java
@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retourne le device_dns du device si connu et actif.
     * SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true
     */
    public Optional<String> findDnsByDeviceId(String deviceId) {
        List<String> results = jdbcTemplate.queryForList(
                "SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true",
                String.class, deviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}

// IotHttpClient.java
@Component
public class IotHttpClient {

    private static final Logger log = LoggerFactory.getLogger(IotHttpClient.class);

    private final RestClient restClient;

    public IotHttpClient(@Value("${iot.http.device-gateway-url:http://wiremock:8080}") String gatewayUrl,
                          RestClient.Builder builder) {
        this.restClient = builder.baseUrl(gatewayUrl).build();
    }

    /**
     * Envoie une commande HTTP POST au device (via gateway/WireMock).
     * Retourne le code HTTP de réponse.
     */
    public int sendCommand(String deviceDns, String payload) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/command")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().value();
        } catch (Exception e) {
            log.error("action=send_command_failed dns={}", deviceDns, e);
            return -1;
        }
    }
}
```

```yaml
# application.yaml
spring:
  application.name: iot-dispatcher
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: iot-dispatcher
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/sut_devices}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:changeme}
    hikari:
      maximum-pool-size: 10
  sql:
    init:
      mode: never   # schema géré par docker-compose-sut

iot:
  topics:
    commands: ${IOT_TOPIC_COMMANDS:iot-commands}
  kafka:
    group-id: ${IOT_KAFKA_GROUP_ID:iot-dispatcher}
    concurrency: ${IOT_KAFKA_CONCURRENCY:3}
  http:
    device-gateway-url: ${IOT_DEVICE_GATEWAY_URL:http://localhost:8090}

management:
  endpoints.web.exposure.include: health,info

logging:
  level:
    com.performance.examples: INFO
```

---

## Règles Spécifiques

- **Pas de `ObjectMapper` pour parser le JSON** : extraction minimaliste par index de string. Le SUT doit rester simple et lisible, sans dépendances superflues.
- **`@KafkaListener concurrency`** : configurable via env var `IOT_KAFKA_CONCURRENCY`. Défaut 3 → 3 consumer threads traitent en parallèle.
- **Error handling** : catch + log WARN/ERROR sans rethrow → le message est considéré traité (acked). Pas de DLQ dans cet exemple.
- **`spring.sql.init.mode: never`** : le schéma est géré par le docker-compose-sut (init scripts PostgreSQL). Pas de Flyway dans le SUT.
- **Dockerfile** : créer un Dockerfile simple `FROM eclipse-temurin:21-jre-alpine` (pas 25 — Spring Boot 3.4.x tourne sur Java 21). Builder stage avec `mvn package -DskipTests`.

---

## Critères de Done

- [ ] `mvn package -f platform-examples/iot-dispatcher/pom.xml -DskipTests` → BUILD SUCCESS
- [ ] `mvn test -f platform-examples/iot-dispatcher/pom.xml` → tests unitaires OK (mock Kafka, mock DB)
- [ ] Test : message avec `device_id` connu → `IotHttpClient.sendCommand()` appelé
- [ ] Test : message avec `device_id` inconnu → log WARN, pas d'appel HTTP
- [ ] Test : message JSON malformé (pas de `device_id`) → log WARN, pas d'exception
- [ ] `Dockerfile` présent dans `platform-examples/iot-dispatcher/`
- [ ] `.claude/progress.md` mis à jour : ISSUE-096 → DONE
