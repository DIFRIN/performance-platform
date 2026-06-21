# ISSUE-097 — SUT device-api (Spring Boot : HTTP → DB → Kafka)

**PDR** : PDR-023
**Module** : `platform-examples/device-api/` (standalone)
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-098 (DB schema doit être DONE)
**Estime** : M

---

## Objectif

Créer le second service SUT : un microservice Spring Boot 3.4.x qui expose une API REST.
Quand il reçoit `POST /api/events?device_id=xxx` :
1. Vérifie si `device_id` existe dans la table `devices` (DB lookup)
2. Publie un événement dans Kafka (`device-events` topic)
3. Retourne `{ "device_id": "xxx", "known": true/false, "event_published": true }`

Ce service est la cible de charge Gatling dans le scénario `device-api-local.yaml`.

---

## Fichiers à Créer

```
platform-examples/device-api/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/performance/examples/device/
    │   │   ├── DeviceApiApplication.java
    │   │   ├── DeviceEventController.java
    │   │   ├── DeviceRepository.java
    │   │   └── DeviceEventProducer.java
    │   └── resources/
    │       └── application.yaml
    └── test/
        └── java/com/performance/examples/device/
            └── DeviceEventControllerTest.java
```

---

## Code à Implémenter

```java
// DeviceApiApplication.java
@SpringBootApplication
public class DeviceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceApiApplication.class, args);
    }
}

// DeviceEventController.java
@RestController
@RequestMapping("/api")
public class DeviceEventController {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventController.class);

    private final DeviceRepository deviceRepository;
    private final DeviceEventProducer deviceEventProducer;

    public DeviceEventController(DeviceRepository deviceRepository,
                                  DeviceEventProducer deviceEventProducer) {
        this.deviceRepository = deviceRepository;
        this.deviceEventProducer = deviceEventProducer;
    }

    /**
     * POST /api/events?device_id=device-0001
     * Vérifie si le device est connu, publie dans Kafka, retourne le résultat.
     */
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> submitEvent(
            @RequestParam("device_id") String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "device_id is required"));
        }

        boolean known = deviceRepository.existsByDeviceId(deviceId);
        deviceEventProducer.publish(deviceId, known);

        log.info("action=event_submitted device_id={} known={}", deviceId, known);

        return ResponseEntity.ok(Map.of(
                "device_id",       deviceId,
                "known",           known,
                "event_published", true
        ));
    }

    // Health endpoint léger pour les warmup checks
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
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
     * SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)
     */
    public boolean existsByDeviceId(String deviceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)",
                Boolean.class, deviceId);
        return Boolean.TRUE.equals(exists);
    }
}

// DeviceEventProducer.java
@Component
public class DeviceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${device.topics.events:device-events}")
    private String eventsTopic;

    public DeviceEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie un événement device dans Kafka.
     * Message : {"device_id":"xxx","known":true,"timestamp":1234567890}
     */
    public void publish(String deviceId, boolean known) {
        String message = String.format(
                "{\"device_id\":\"%s\",\"known\":%b,\"timestamp\":%d}",
                deviceId, known, System.currentTimeMillis());
        kafkaTemplate.send(eventsTopic, deviceId, message);
        log.debug("action=event_published device_id={} topic={}", deviceId, eventsTopic);
    }
}
```

```yaml
# application.yaml
spring:
  application.name: device-api
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: "1"
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/sut_devices}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:changeme}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  sql:
    init:
      mode: never

device:
  topics:
    events: ${DEVICE_TOPIC_EVENTS:device-events}

server:
  port: ${SERVER_PORT:8082}

management:
  endpoints.web.exposure.include: health,info

logging:
  level:
    com.performance.examples: INFO
```

---

## Règles Spécifiques

- **Réponse toujours 200** : même si `known=false`, retourner 200 OK. Le SUT ne doit pas masquer la charge (les tests Gatling mesurent les 200 OK).
- **Kafka fire-and-forget** : `kafkaTemplate.send()` sans `.get()` → non-bloquant pour maximiser le throughput du SUT sous charge.
- **`acks: "1"`** : compromis performance/durabilité pour le SUT. Changeable via env var.
- **HikariCP** : pool de 20 connexions — assez pour 1 000 users concurrents avec des requêtes courtes.
- **Dockerfile** : `FROM eclipse-temurin:21-jre-alpine`. Builder stage identique à ISSUE-096.

---

## Critères de Done

- [ ] `mvn package -f platform-examples/device-api/pom.xml -DskipTests` → BUILD SUCCESS
- [ ] `mvn test -f platform-examples/device-api/pom.xml` → tests OK
- [ ] Test : `POST /api/events?device_id=device-0001` (known) → 200 `{known: true, event_published: true}`
- [ ] Test : `POST /api/events?device_id=unknown-999` (unknown) → 200 `{known: false, event_published: true}`
- [ ] Test : `POST /api/events` sans `device_id` → 400 Bad Request
- [ ] Test : `GET /api/health` → 200 `{status: UP}`
- [ ] Test : Kafka `kafkaTemplate.send()` appelé dans les deux cas (known + unknown)
- [ ] `Dockerfile` présent dans `platform-examples/device-api/`
- [ ] `.claude/progress.md` mis à jour : ISSUE-097 → DONE
