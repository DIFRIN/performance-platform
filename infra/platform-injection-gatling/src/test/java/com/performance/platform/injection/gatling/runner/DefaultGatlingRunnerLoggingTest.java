package com.performance.platform.injection.gatling.runner;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.injection.gatling.load.DefaultLoadModelTranslator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression : {@code Gatling$.fromArgs} arrete le {@code LoggerContext} Logback
 * global en fin de run, l'application hote ne loggue alors plus rien apres
 * la simulation. Le lancement embarque doit laisser le logging de l'hote intact.
 */
class DefaultGatlingRunnerLoggingTest {

    @TempDir
    Path resultsDir;

    private HttpServer server;
    private String baseUrl;
    private LoggerContext loggerContext;
    private ch.qos.logback.classic.Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ping", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void hostLoggerContextStaysActiveAndCapturesEventsAfterRealSimulation() {
        String simulationId = "logging-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(loggerContext.isStarted()).isTrue();

        Path result = new DefaultGatlingRunner(new DefaultLoadModelTranslator()).run(new GatlingRunConfig(
                MinimalSimulation.class.getName(),
                new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSec", 1, "durationSeconds", 1)),
                Map.of("test.baseUrl", baseUrl),
                resultsDir,
                simulationId,
                Duration.ofSeconds(30)));
        LoggerFactory.getLogger("host.after.gatling").warn("action=after_gatling simulationId={}", simulationId);

        assertThat(result.resolve("simulation.log")).isRegularFile();
        assertThat(loggerContext.isStarted()).isTrue();
        assertThat(rootLogger.isAttached(appender)).isTrue();
        assertThat(appender.isStarted()).isTrue();
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("action=gatling_run_complete simulationId=" + simulationId + " resultsDir=" + result)
                .contains("action=after_gatling simulationId=" + simulationId);
    }
}