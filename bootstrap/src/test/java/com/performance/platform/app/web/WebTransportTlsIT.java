package com.performance.platform.app.web;

import com.performance.platform.app.security.SecurityConfiguration;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.config.TransportConfiguration;
import com.performance.platform.transport.http.HttpExecutionTransport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebTransportTlsIT {

    @TempDir
    static Path temporaryDirectory;
    private static Path certificates;

    @BeforeAll
    static void certificates() throws Exception {
        certificates = temporaryDirectory.resolve("certificates");
        Path script = Path.of("..", "distribution", "example", "tls", "generate-test-certificates.sh")
                .toAbsolutePath().normalize();
        Path log = temporaryDirectory.resolve("openssl.log");
        var process = new ProcessBuilder("sh", script.toString(), certificates.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("TLS certificate generation timed out");
        }
        assertThat(process.exitValue()).withFailMessage(() -> {
            try {
                return Files.readString(log);
            } catch (IOException e) {
                return e.toString();
            }
        }).isZero();
    }

    @Test
    void browserWithoutClientCertificateCanUseBootHttpsWhileTransportRequiresMutualTls() throws Exception {
        try (var context = new SpringApplicationBuilder(WebApplication.class).web(WebApplicationType.SERVLET)
                .run(arguments())) {
            var transport = (HttpExecutionTransport) context.getBean(ExecutionTransport.class);
            transport.connect();
            try {
                var bundles = context.getBean(SslBundles.class);
                try (var browser = HttpClient.newBuilder().sslContext(bundles.getBundle("browser").createSslContext())
                        .connectTimeout(Duration.ofSeconds(5)).build()) {
                    int webPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                    var uiRequest = get("https://localhost:" + webPort + "/index.html");
                    var response = browser.send(uiRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).isEqualTo("UI");
                    assertThat(browser.send(get("https://localhost:" + webPort + "/api/v1/agents"),
                            HttpResponse.BodyHandlers.discarding()).statusCode()).isIn(401, 403);
                    assertThatThrownBy(() -> browser.send(get(transport.callbackUrl()),
                            HttpResponse.BodyHandlers.discarding())).isInstanceOf(IOException.class);
                }
                try (var agent = HttpClient.newBuilder().sslContext(bundles.getBundle("transport").createSslContext())
                        .connectTimeout(Duration.ofSeconds(5)).build()) {
                    var invalidUi = get(transport.callbackUrl() + "/index.html");
                    assertThat(agent.send(invalidUi, HttpResponse.BodyHandlers.discarding()).statusCode())
                            .isEqualTo(405);
                }
            } finally {
                transport.disconnect();
            }
        }
    }

    @Test
    void agentTransportTlsListenerDoesNotRequireServletWebApplication() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var args = new ArrayList<>(List.of(arguments()));
        args.remove("--transport.http.listener.port=0");
        args.add("--runtime.role=AGENT");
        args.add("--transport.http.listener.port=" + port);
        args.add("--transport.http.listener.advertised-url=https://localhost:" + port);
        args.add("--transport.http.listener.orchestrator-url=https://localhost:8092");
        try (var context = new SpringApplicationBuilder(AgentApplication.class).web(WebApplicationType.NONE)
                .run(args.toArray(String[]::new))) {
            assertThat(context.getEnvironment().getProperty("local.server.port")).isNull();
            var transport = (HttpExecutionTransport) context.getBean(ExecutionTransport.class);
            transport.connect();
            try (var client = HttpClient.newBuilder().sslContext(
                    context.getBean(SslBundles.class).getBundle("transport").createSslContext()).build()) {
                assertThat(client.send(get(transport.callbackUrl()), HttpResponse.BodyHandlers.discarding()).statusCode())
                        .isEqualTo(405);
            } finally {
                transport.disconnect();
            }
        }
    }

    private static String[] arguments() {
        return new String[] {
                "--spring.config.location=optional:classpath:/tls-test-empty.yaml",
                "--spring.main.banner-mode=off",
                "--server.port=0",
                "--server.ssl.enabled=true",
                "--server.ssl.bundle=web",
                "--server.ssl.client-auth=NONE",
                "--platform.security.enabled=true",
                "--transport.type=HTTP",
                "--transport.tls.enabled=true",
                "--transport.tls.bundle=transport",
                "--transport.http.listener.bind-address=127.0.0.1",
                "--transport.http.listener.port=0",
                "--spring.ssl.bundle.pem.web.keystore.certificate=" + file("ui.crt"),
                "--spring.ssl.bundle.pem.web.keystore.private-key=" + file("ui.key"),
                "--spring.ssl.bundle.pem.transport.keystore.certificate=" + file("agent.crt"),
                "--spring.ssl.bundle.pem.transport.keystore.private-key=" + file("agent.key"),
                "--spring.ssl.bundle.pem.transport.truststore.certificate=" + file("ca.crt"),
                "--spring.ssl.bundle.pem.browser.truststore.certificate=" + file("ca.crt")
        };
    }

    private static String file(String name) {
        return certificates.resolve(name).toUri().toString();
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({TransportConfiguration.class, SecurityConfiguration.class, UiController.class})
    static class WebApplication {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(TransportConfiguration.class)
    static class AgentApplication {
    }

    @RestController
    static class UiController {
        @GetMapping("/index.html")
        String index() {
            return "UI";
        }
    }
}
