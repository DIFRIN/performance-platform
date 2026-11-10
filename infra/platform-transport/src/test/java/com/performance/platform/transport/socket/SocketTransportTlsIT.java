package com.performance.platform.transport.socket;

import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.config.SocketTransportProperties;
import com.performance.platform.transport.tls.TlsTestCertificates;
import com.performance.platform.transport.tls.TransportTls;
import com.performance.platform.transport.tls.TransportTlsIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class SocketTransportTlsIT {

    @ParameterizedTest
    @ValueSource(strings = {"PEM", "PKCS12"})
    void deliversMessagesWithMutualTls(String format) throws Exception {
        int port = freePort();
        var props = new SocketTransportProperties("localhost", port, 10, true, 100);
        var orchestrator = new SocketExecutionTransport(props, tls(format, "server"), "ORCHESTRATOR");
        var agent = new SocketExecutionTransport(props, tls(format, "agent"), "AGENT");
        try {
            orchestrator.connect();
            agent.connect();
            await().atMost(Duration.ofSeconds(5)).until(() -> orchestrator.activeConnectionCount() == 1);
            TransportTlsIT.verifyDelivery(orchestrator, agent);
        } finally {
            agent.disconnect();
            orchestrator.disconnect();
        }
    }

    @Test
    void rejectsUntrustedAndAnonymousClientsBeforeRegisteringThem() throws Exception {
        int port = freePort();
        var props = new SocketTransportProperties("localhost", port, 10, true, 100);
        var orchestrator = new SocketExecutionTransport(props, tls("PEM", "server"), "ORCHESTRATOR");
        try {
            orchestrator.connect();
            for (String identity : new String[] {null, "rogue"}) {
                try (var socket = (SSLSocket) TlsTestCertificates.pem(identity).createSslContext()
                        .getSocketFactory().createSocket("localhost", port)) {
                    socket.setSoTimeout(5000);
                    assertThatThrownBy(() -> {
                        socket.startHandshake();
                        socket.getInputStream().read();
                    }).isInstanceOf(IOException.class);
                }
            }
            assertThat(orchestrator.activeConnectionCount()).isZero();
            var wrongHost = new SocketExecutionTransport(
                    new SocketTransportProperties("127.0.0.2", port, 10, true, 100),
                    tls("PEM", "agent"), "AGENT");
            assertThatThrownBy(wrongHost::connect).isInstanceOf(TransportException.class)
                    .hasRootCauseInstanceOf(java.security.cert.CertificateException.class);
            assertThat(wrongHost.isConnected()).isFalse();
        } finally {
            orchestrator.disconnect();
        }
    }

    private static TransportTls tls(String format, String identity) {
        return new TransportTls(format.equals("PKCS12") ? TlsTestCertificates.pkcs12(identity)
                : TlsTestCertificates.pem(identity), true);
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
