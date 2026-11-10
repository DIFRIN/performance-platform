# Optional transport mTLS and independent UI HTTPS

Transport TLS is **off by default**. No global JVM SSL setting is changed.
Task-executor HTTP/Kafka clients targeting the SUT are unaffected.
UI TLS never inherits the transport client-certificate policy.

## Configuration

Use normal Spring configuration (including environment variables) on each node:

```yaml
transport:
  tls:
    enabled: true
    bundle: transport
    client-auth: NEED

spring:
  ssl:
    bundle:
      pem:
        transport:
          keystore:
            certificate: file:/certificates/agent.crt
            private-key: file:/certificates/agent.key
          truststore:
            certificate: file:/certificates/transport-ca.crt
```

`NEED` is the default: certificate, private key and CA/truststore are required.
`NONE` supports one-way transport TLS; HTTP/Socket listeners still require a
server identity. Kafka/RabbitMQ brokers must separately enforce mTLS.
Hostname verification is always enabled; certificate SANs must match connection
hostnames. Bundle protocols/ciphers apply to all four transports.

Provided external configurations:

| Transport | UI/API |
|---|---|
| [transport-pem.yaml](transport-pem.yaml) | [web-pem.yaml](web-pem.yaml) |
| [transport-pkcs12.yaml](transport-pkcs12.yaml) | [web-pkcs12.yaml](web-pkcs12.yaml) |

Each process can choose its own files/identity and any standard Boot bundle name.
PKCS12 uses Boot's `spring.ssl.bundle.jks` namespace; actual JKS stores work by
changing the store type to `JKS`. Supply passwords through environment variables
or secret mounts, never scenario YAML or committed files.

For example, from **WSL**:

```sh
export TRANSPORT_TLS_CERT=file:/run/secrets/agent.crt
export TRANSPORT_TLS_KEY=file:/run/secrets/agent.key
export TRANSPORT_TLS_CA=file:/run/secrets/transport-ca.crt
MODE=AGENT java -jar bootstrap/target/platform-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.config.additional-location=file:distribution/example/tls/transport-pem.yaml \
  --transport.type=HTTP \
  --transport.http.listener.port=8091 \
  --transport.http.listener.advertised-url=https://agent.example.net:8091 \
  --transport.http.listener.orchestrator-url=https://orchestrator.example.net:8091
```

Activate UI HTTPS independently on LOCAL/ORCHESTRATOR with `web-pem.yaml` or
`web-pkcs12.yaml`. It sets `server.ssl.client-auth=NONE`: browsers need no client
certificate. Existing API OAuth2/JWT permissions remain unchanged.
AGENT has no UI/API/Actuator servlet listener even when `server.ssl` is configured.

## HTTP transport

`transport.http.listener` properties:

| Property | Default / meaning |
|---|---|
| `enabled` | `true` for HTTP transport |
| `bind-address` | `0.0.0.0` |
| `port` | `8091`, independent of `server.port` |
| `advertised-url` | Required on agents; origin only, reachable from orchestrator |
| `orchestrator-url` | Required on agents; origin only |

The shared `transport.http.callback-base-path` defaults to `/api/callback`.
AGENT accepts POST at that path (tasks) and `/signals`; ORCHESTRATOR accepts
`/events` and `/agents`. UI/API routes are never exposed on this connector.
Accepted delivery returns 202; invalid payloads return 400, unsupported routes
404, unsupported methods 405, and bodies exceeding 16 MiB 413.
URLs must use HTTPS when transport TLS is enabled; failures propagate rather
than being reported as successful command dispatch.

## Socket transport

Set `transport.type=SOCKET`, `transport.socket.orchestrator-host`,
`transport.socket.orchestrator-port`, and `runtime.role=ORCHESTRATOR` or `AGENT`.
The ORCHESTRATOR binds; AGENT connects and reconnects with the same SSL bundle.
TLS never infers the runtime role from whether a local port can be bound.
Peer handshakes are verified before adding a connection to the registry.

## Kafka broker

The transport factories set `security.protocol=SSL` for both producer and consumer.
Set `transport.kafka.bootstrap-servers` to TLS listeners, not plaintext ports.
For a broker, equivalent `server.properties` settings are:

```properties
listeners=SSL://0.0.0.0:9093
advertised.listeners=SSL://kafka.example.net:9093
listener.security.protocol.map=SSL:SSL
ssl.client.auth=required
ssl.keystore.type=PKCS12
ssl.keystore.location=/run/secrets/broker.p12
ssl.truststore.type=PKCS12
ssl.truststore.location=/run/secrets/transport-truststore.p12
```

Provide `ssl.keystore.password`, `ssl.key.password`, and `ssl.truststore.password`
through your broker secret-management mechanism. Configure inter-broker and KRaft
controller listeners/security separately for your cluster; this snippet is only
the external listener. Broker certificates need server SANs; all client
certificates must be signed by a CA trusted by the broker.

## RabbitMQ broker

Set `transport.type=RABBITMQ`, host, TLS port (typically 5671), vhost, username and
password as before. Transport mTLS does not replace RabbitMQ account permissions.
Broker `rabbitmq.conf`:

```properties
listeners.tcp = none
listeners.ssl.default = 5671
ssl_options.cacertfile = /run/secrets/transport-ca.crt
ssl_options.certfile = /run/secrets/rabbitmq.crt
ssl_options.keyfile = /run/secrets/rabbitmq.key
ssl_options.verify = verify_peer
ssl_options.fail_if_no_peer_cert = true
```

## Isolated local HTTP + UI demo (WSL)

The [Compose file](docker-compose.yaml) creates a separate project, without
restarting the existing example stack. Host ports are 8443 (UI) and 8444
(orchestrator mTLS transport). The agent transport stays inside the Docker network.

```sh
sh distribution/example/tls/generate-test-certificates.sh
export TLS_EXAMPLE_DB_PASSWORD="$(openssl rand -hex 24)"
docker compose -f distribution/example/tls/docker-compose.yaml config --quiet
docker compose -f distribution/example/tls/docker-compose.yaml up -d --build

# One-way UI HTTPS: no client certificate.
curl --fail --cacert distribution/example/tls/generated/ca.crt \
  https://localhost:8443/index.html

# mTLS listener: must fail without a client certificate.
curl --cacert distribution/example/tls/generated/ca.crt \
  https://localhost:8444/api/callback/events

# With client identity, handshake succeeds; GET returns 405 (POST-only).
sudo curl --cacert distribution/example/tls/generated/ca.crt \
  --cert distribution/example/tls/generated/agent.crt \
  --key distribution/example/tls/generated/agent.key \
  https://localhost:8444/api/callback/events

docker compose -f distribution/example/tls/docker-compose.yaml down
```

The init service gives only application identity keys to the non-root platform
user (mode 600). CA signing keys and rogue test identities are **not** mounted
into application containers. To read the agent key from the WSL user after init,
run the client-cert curl with `sudo`, or use the generated PKCS12 identity with
appropriate curl support. Protect production keys through Docker/Kubernetes
secrets instead of this local permission initializer.

The generator refuses to overwrite an existing directory. Certificates expire
in seven days and are **test-only**. Never commit keys, truststores containing
private keys, or passwords. Renewal requires restarting affected nodes.
The Maven build-only `MAVEN_TLS_INSECURE` option is unrelated to runtime TLS.

## Automated verification (WSL)

```sh
mvn verify -pl :platform-bootstrap -am -P integration-tests \
  -Dtest=TransportTlsTest,TransportConfigurationTest,HttpExecutionTransportTest,SocketExecutionTransportTest,AgentRuntimeConfigurationTest,WebSecurityTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=TransportTlsIT,SocketTransportTlsIT,KafkaTransportTlsIT,RabbitMQTransportTlsIT,WebTransportTlsIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

Requires Java 25, Maven, OpenSSL, keytool, and Docker in WSL. Broker images can be
overridden with `-Dtest.kafka.image=...` and `-Dtest.rabbitmq.image=...`.
Tests generate ephemeral certificates under ignored build output or JUnit temp
directories and run isolated real Kafka/RabbitMQ containers. They cover PEM and
PKCS12, message delivery, rejected missing/untrusted client certificates,
hostname verification, plaintext regressions, browser access without client
certificates, and HTTP transport in a non-web AGENT context.
