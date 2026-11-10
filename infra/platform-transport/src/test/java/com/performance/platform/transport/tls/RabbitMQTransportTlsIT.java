package com.performance.platform.transport.tls;

import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.config.RabbitMQTransportProperties;
import com.performance.platform.transport.rabbitmq.RabbitMQExecutionTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.images.builder.Transferable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RabbitMQTransportTlsIT {

    @Container
    static final RabbitMQContainer RABBIT = broker();

    private static RabbitMQContainer broker() {
        var broker = new RabbitMQContainer(DockerImageName.parse(
                System.getProperty("test.rabbitmq.image", "rabbitmq:3.13-management-alpine")));
        broker.addExposedPort(5671);
        broker.withCopyFileToContainer(file("server.key"), "/etc/rabbitmq/server.key");
        broker.withCopyFileToContainer(file("server.crt"), "/etc/rabbitmq/server.crt");
        broker.withCopyFileToContainer(file("ca.crt"), "/etc/rabbitmq/ca.crt");
        broker.withCopyToContainer(Transferable.of("""
                listeners.tcp = none
                listeners.ssl.default = 5671
                ssl_options.cacertfile = /etc/rabbitmq/ca.crt
                ssl_options.certfile = /etc/rabbitmq/server.crt
                ssl_options.keyfile = /etc/rabbitmq/server.key
                ssl_options.verify = verify_peer
                ssl_options.fail_if_no_peer_cert = true
                loopback_users.guest = false
                """, 0444), "/etc/rabbitmq/rabbitmq.conf");
        return broker;
    }

    @ParameterizedTest
    @ValueSource(strings = {"PEM", "PKCS12"})
    void exchangesTransportMessagesWithMutualTls(String format) {
        String prefix = UUID.randomUUID().toString();
        var props = properties(RABBIT.getHost(), prefix);
        var orchestrator = new RabbitMQExecutionTransport(props, tls(format, "server"));
        var agent = new RabbitMQExecutionTransport(props, tls(format, "agent"));
        try {
            orchestrator.connect();
            agent.connect();
            TransportTlsIT.verifyDelivery(orchestrator, agent);
        } finally {
            agent.disconnect();
            orchestrator.disconnect();
        }
    }

    @Test
    void brokerRejectsAnonymousAndUntrustedClientsAndWrongHostname() {
        for (String identity : new String[] {null, "rogue"}) {
            var transport = new RabbitMQExecutionTransport(properties(RABBIT.getHost(), "rejected"),
                    new TransportTls(TlsTestCertificates.pem(identity), false));
            try {
                assertThatThrownBy(transport::connect).isInstanceOf(TransportException.class);
            } finally {
                transport.disconnect();
            }
        }
        var wrongHost = new RabbitMQExecutionTransport(properties("127.0.0.2", "wrong-host"), tls("PEM", "agent"));
        assertThatThrownBy(wrongHost::connect).isInstanceOf(TransportException.class)
                .hasRootCauseInstanceOf(java.security.cert.CertificateException.class);
    }

    private static RabbitMQTransportProperties properties(String host, String prefix) {
        return new RabbitMQTransportProperties(host, RABBIT.getAmqpsPort(), "/",
                "tasks-" + prefix, "events-" + prefix, "signals-" + prefix,
                RABBIT.getAdminUsername(), RABBIT.getAdminPassword());
    }

    private static MountableFile file(String name) {
        return MountableFile.forHostPath(TlsTestCertificates.DIRECTORY.resolve(name), 0444);
    }

    private static TransportTls tls(String format, String identity) {
        return new TransportTls(format.equals("PKCS12") ? TlsTestCertificates.pkcs12(identity)
                : TlsTestCertificates.pem(identity), true);
    }
}
