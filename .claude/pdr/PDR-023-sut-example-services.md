# PDR-023 — SUT Example Services (IoT Use Cases)

**Module Maven** : `platform-examples/` (standalone — NON inclus dans le root pom.xml multi-module)
**Package** : `com.performance.examples.iot` / `com.performance.examples.device`
**Statut** : WAITING
**Specs de référence** : aucune spec plateforme — ce sont des services cibles indépendants
**Dépend de** : ISSUE-098 (DB schema) doit précéder ISSUE-096 et ISSUE-097 (les services utilisent le même schéma)
**Issues** : ISSUE-096, ISSUE-097, ISSUE-098

---

## Responsabilité

Crée deux microservices Spring Boot indépendants servant de **Système Sous Test (SUT)** pour démontrer concrètement les capacités de la performance platform.

Ces services sont volontairement **simples** (pas de couche service, pas de repo pattern) — leur but est d'être un SUT lisible et réaliste, pas un exemple d'architecture.

**SUT-A `iot-dispatcher`** : consomme des commandes depuis Kafka → vérifie que le `device_id` est connu en DB → récupère le `device_dns` → envoie la commande HTTP au device IoT.

**SUT-B `device-api`** : reçoit une requête HTTP POST → vérifie si le `device_id` existe en DB → publie un événement dans Kafka.

Les **IoT devices** (destinataires HTTP du dispatcher) sont simulés par WireMock (déjà dans le docker-compose plateforme). Les services IoT réels (100k) sont remplacés par WireMock dans les exemples.

**Stack SUT** : Spring Boot 3.4.x (pas 4.x — indépendant de la plateforme), Spring Kafka, JdbcTemplate, RestClient. Pas de JPA (schema simple, requêtes directes JDBC).

**Lancé séparément** : le SUT a son propre `docker-compose-sut.yaml` (PDR-024). Il ne fait pas partie du build Maven de la plateforme.

---

## Structure des Services

### SUT-A : `iot-dispatcher`

```
platform-examples/iot-dispatcher/
├── pom.xml                              (Spring Boot 3.4.x, spring-kafka, postgresql)
└── src/main/
    ├── java/com/performance/examples/iot/
    │   ├── IotDispatcherApplication.java
    │   ├── DeviceCommandConsumer.java   @KafkaListener(topics="${iot.topics.commands}")
    │   ├── DeviceRepository.java        JdbcTemplate — findDnsByDeviceId(String) : Optional<String>
    │   └── IotHttpClient.java           RestClient — sendCommand(String deviceDns, String payload)
    └── resources/
        └── application.yaml
```

```java
// DeviceCommandConsumer.java
@Component
public class DeviceCommandConsumer {

    @KafkaListener(topics = "${iot.topics.commands:iot-commands}", groupId = "${iot.kafka.group-id:iot-dispatcher}")
    public void handle(String message) {
        // 1. extraire device_id du JSON message
        // 2. deviceRepository.findDnsByDeviceId(deviceId)
        // 3. si trouvé et is_active=true → iotHttpClient.sendCommand(dns, message)
        // 4. log résultat (action=command_dispatched device_id=xxx dns=xxx status=200)
    }
}

// DeviceRepository.java
@Repository
public class DeviceRepository {
    // JdbcTemplate injecté
    public Optional<String> findDnsByDeviceId(String deviceId);
    // SELECT device_dns FROM devices WHERE device_id = ? AND is_active = true
}

// IotHttpClient.java
@Component
public class IotHttpClient {
    // RestClient injecté (base-url depuis ${iot.http.device-gateway-url})
    public int sendCommand(String deviceDns, String payload);
    // POST http://{deviceDns}/command → retourne status HTTP
}
```

```yaml
# application.yaml (iot-dispatcher)
spring:
  application.name: iot-dispatcher
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: iot-dispatcher
      auto-offset-reset: earliest
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/sut_devices}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:changeme}

iot:
  topics:
    commands: ${IOT_TOPIC_COMMANDS:iot-commands}
  http:
    device-gateway-url: ${IOT_DEVICE_GATEWAY_URL:http://wiremock:8080}
```

---

### SUT-B : `device-api`

```
platform-examples/device-api/
├── pom.xml                              (Spring Boot 3.4.x, spring-kafka, postgresql)
└── src/main/
    ├── java/com/performance/examples/device/
    │   ├── DeviceApiApplication.java
    │   ├── DeviceEventController.java   REST POST /api/events
    │   ├── DeviceRepository.java        JdbcTemplate — existsByDeviceId(String) : boolean
    │   └── DeviceEventProducer.java     KafkaTemplate — publish(String deviceId, boolean known)
    └── resources/
        └── application.yaml
```

```java
// DeviceEventController.java
@RestController
@RequestMapping("/api")
public class DeviceEventController {

    // POST /api/events?device_id=xxx
    // 1. deviceRepository.existsByDeviceId(deviceId)
    // 2. deviceEventProducer.publish(deviceId, exists)
    // 3. retourne 200 { "device_id": "xxx", "known": true/false, "event_published": true }
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> submitEvent(@RequestParam("device_id") String deviceId);
}

// DeviceRepository.java
@Repository
public class DeviceRepository {
    public boolean existsByDeviceId(String deviceId);
    // SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = ?)
}

// DeviceEventProducer.java
@Component
public class DeviceEventProducer {
    // KafkaTemplate<String, String> injecté
    public void publish(String deviceId, boolean known);
    // topic: ${device.topics.events:device-events}
    // message: {"device_id":"xxx","known":true,"timestamp":1234567890}
}
```

```yaml
# application.yaml (device-api)
spring:
  application.name: device-api
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/sut_devices}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:changeme}

device:
  topics:
    events: ${DEVICE_TOPIC_EVENTS:device-events}

server:
  port: ${SERVER_PORT:8082}
```

---

### DB Schema partagé : `sut-db/`

```
platform-examples/sut-db/sql/
├── V1__devices_schema.sql
└── V2__seed_10k_devices.sql
```

```sql
-- V1__devices_schema.sql
CREATE TABLE IF NOT EXISTS devices (
    id         SERIAL PRIMARY KEY,
    device_id  VARCHAR(50)  NOT NULL UNIQUE,
    device_dns VARCHAR(255) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_devices_device_id ON devices(device_id);
CREATE INDEX idx_devices_active ON devices(is_active) WHERE is_active = true;

-- V2__seed_10k_devices.sql
-- Insère 10 000 devices (device-0001 → device-9999 + device-10000)
-- device_dns pointe vers wiremock (simulateur IoT dans docker-compose-sut)
INSERT INTO devices (device_id, device_dns, is_active)
SELECT
    'device-' || LPAD(gs::text, 4, '0'),
    'wiremock:8080',   -- WireMock simule tous les endpoints IoT
    true
FROM generate_series(1, 10000) gs
ON CONFLICT (device_id) DO NOTHING;
```

---

## Règles de Comportement

- **`iot-dispatcher`** : si `device_id` inconnu en DB → log WARN, skip (pas d'exception levée). Pas de retry interne (la plateforme gère la retry via `RetryPolicy`).
- **`device-api`** : toujours publier dans Kafka (même si `known=false`). Retourner 200 OK dans tous les cas (le SUT ne doit pas masquer la charge pour la plateforme de test).
- **WireMock** : tous les `device_dns` pointent vers `wiremock:8080`. WireMock retourne 200 OK pour `POST /command` (stub défini dans `docker-compose-sut.yaml` via volume).
- **Pas de Flyway dans les services** : la migration SQL est exécutée par le docker-compose SUT directement (script init PostgreSQL). Les services utilisent `spring.sql.init.mode=never`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PostgreSQL (partagé avec plateforme dans docker-compose-sut)
  Kafka (partagé avec plateforme dans docker-compose-sut)
  WireMock (service HTTP mock pour les IoT devices)

Ce PDR est utilisé par :
  PDR-024 (docker-compose-sut.yaml inclut ces services)
  PDR-024 (scenarios YAML testent ces services)
```

---

## Critères de Done (PDR complet)

- [ ] ISSUE-096, 097, 098 toutes DONE
- [ ] `mvn package -f platform-examples/iot-dispatcher/pom.xml -q` → BUILD SUCCESS
- [ ] `mvn package -f platform-examples/device-api/pom.xml -q` → BUILD SUCCESS
- [ ] `platform-examples/sut-db/sql/V1__devices_schema.sql` — syntaxe PostgreSQL valide
- [ ] `platform-examples/sut-db/sql/V2__seed_10k_devices.sql` — 10 000 rows attendus
