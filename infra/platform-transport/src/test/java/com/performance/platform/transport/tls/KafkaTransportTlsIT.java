package com.performance.platform.transport.tls;

import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.config.KafkaTransportBeans;
import com.performance.platform.transport.config.TransportConfiguration;
import com.performance.platform.transport.kafka.BundleSslEngineFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class KafkaTransportTlsIT {

    @Container
    static final TlsKafkaContainer KAFKA = new TlsKafkaContainer();

    @SuppressWarnings("deprecation")
    private static final class TlsKafkaContainer extends KafkaContainer {
        private TlsKafkaContainer() {
            super(DockerImageName.parse(System.getProperty("test.kafka.image", "confluentinc/cp-kafka:7.8.0")));
            try {
                for (String file : new String[] {"server.p12", "truststore.p12"}) {
                    withCopyToContainer(Transferable.of(Files.readAllBytes(TlsTestCertificates.DIRECTORY.resolve(file)),
                            0444), "/tls/" + file);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot prepare Kafka TLS stores", e);
            }
        }

        @Override
        protected void configure() {
            super.configure();
            addEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,PLAINTEXT:SSL");
            addEnv("KAFKA_SSL_KEYSTORE_LOCATION", "/tls/server.p12");
            addEnv("KAFKA_SSL_KEYSTORE_PASSWORD", "changeit");
            addEnv("KAFKA_SSL_KEY_PASSWORD", "changeit");
            addEnv("KAFKA_SSL_KEYSTORE_TYPE", "PKCS12");
            addEnv("KAFKA_SSL_TRUSTSTORE_LOCATION", "/tls/truststore.p12");
            addEnv("KAFKA_SSL_TRUSTSTORE_PASSWORD", "changeit");
            addEnv("KAFKA_SSL_TRUSTSTORE_TYPE", "PKCS12");
            addEnv("KAFKA_SSL_CLIENT_AUTH", "required");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PEM", "PKCS12"})
    void bothSpringProducerAndConsumerUseBundleAndExchangeTransportMessages(String format) {
        String prefix = UUID.randomUUID().toString();
        context(format, "server", "orchestrator-" + prefix, prefix).run(orchestratorContext -> {
            assertThat(orchestratorContext).hasNotFailed();
            ProducerFactory<?, ?> producerFactory = orchestratorContext.getBean(ProducerFactory.class);
            ConsumerFactory<?, ?> consumerFactory = orchestratorContext.getBean(ConsumerFactory.class);
            assertThat(producerFactory.getConfigurationProperties())
                    .containsEntry("security.protocol", "SSL")
                    .containsEntry("ssl.engine.factory.class", BundleSslEngineFactory.class);
            assertThat(consumerFactory.getConfigurationProperties())
                    .containsKey(BundleSslEngineFactory.BUNDLE_CONFIG);
            var orchestrator = orchestratorContext.getBean(ExecutionTransport.class);
            context(format, "agent", "agent-" + prefix, prefix).run(agentContext -> {
                assertThat(agentContext).hasNotFailed();
                var agent = agentContext.getBean(ExecutionTransport.class);
                try {
                    orchestrator.connect();
                    agent.connect();
                    TransportTlsIT.verifyDelivery(orchestrator, agent);
                } finally {
                    agent.disconnect();
                    orchestrator.disconnect();
                }
            });
        });
    }

    @Test
    void brokerRejectsAnonymousAndUntrustedClientCertificates() {
        for (String identity : new String[] {null, "rogue"}) {
            Map<String, Object> config = Map.of(
                    "bootstrap.servers", KAFKA.getBootstrapServers(),
                    "security.protocol", "SSL",
                    "ssl.engine.factory.class", BundleSslEngineFactory.class,
                    BundleSslEngineFactory.BUNDLE_CONFIG, TlsTestCertificates.pem(identity),
                    "key.serializer", StringSerializer.class,
                    "value.serializer", ByteArraySerializer.class,
                    "max.block.ms", 10000,
                    "request.timeout.ms", 5000,
                    "delivery.timeout.ms", 10000);
            try (var producer = new KafkaProducer<String, byte[]>(config)) {
                assertThatThrownBy(() -> producer.send(new ProducerRecord<>("rejected-client", new byte[0]))
                        .get(15, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(SslAuthenticationException.class);
            }
        }
    }

    @Test
    void verifiesBrokerHostnameRatherThanJustTrustingItsCa() {
        Map<String, Object> config = Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers().replace(KAFKA.getHost(), "127.0.0.2"),
                "security.protocol", "SSL",
                "ssl.engine.factory.class", BundleSslEngineFactory.class,
                BundleSslEngineFactory.BUNDLE_CONFIG, TlsTestCertificates.pem("agent"),
                "key.serializer", StringSerializer.class,
                "value.serializer", ByteArraySerializer.class,
                "max.block.ms", 10000);
        try (var producer = new KafkaProducer<String, byte[]>(config)) {
            assertThatThrownBy(() -> producer.send(new ProducerRecord<>("wrong-host", new byte[0]))
                    .get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(SslAuthenticationException.class)
                    .hasRootCauseInstanceOf(java.security.cert.CertificateException.class);
        }
    }

    private static ApplicationContextRunner context(String format, String identity, String group, String prefix) {
        var bundles = new DefaultSslBundleRegistry();
        bundles.registerBundle("transport", format.equals("PKCS12")
                ? TlsTestCertificates.pkcs12(identity) : TlsTestCertificates.pem(identity));
        return new ApplicationContextRunner()
                .withUserConfiguration(TransportConfiguration.class, KafkaTransportBeans.class)
                .withBean(SslBundles.class, () -> bundles)
                .withPropertyValues("transport.type=KAFKA", "transport.tls.enabled=true",
                        "transport.tls.bundle=transport",
                        "transport.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "transport.kafka.consumer-group=" + group,
                        "transport.kafka.producer-acks=all",
                        "transport.kafka.tasks-topic=tasks-" + prefix,
                        "transport.kafka.events-topic=events-" + prefix,
                        "transport.kafka.signals-topic=signals-" + prefix);
    }
}
