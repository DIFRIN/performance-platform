package com.performance.platform.assertion;

import com.performance.platform.assertion.database.DatabaseAssertionExecutor;
import com.performance.platform.assertion.file.FileAssertionExecutor;
import com.performance.platform.assertion.gatling.GatlingMetricAssertionExecutor;
import com.performance.platform.assertion.httpmock.HttpMockAssertionExecutor;
import com.performance.platform.assertion.kafka.KafkaAssertionExecutor;
import com.performance.platform.assertion.wiremock.WireMockAssertionExecutor;
import com.performance.platform.infrastructure.executor.DefaultTaskExecutorRegistry;
import com.performance.platform.infrastructure.executor.TaskExecutorRegistry;
import com.performance.platform.infrastructure.executor.http.HttpTargetRegistry;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test verifiant que les 6 {@link AssertionExecutor} sont compatibles avec
 * {@link DefaultTaskExecutorRegistry} en tant que beans {@link TaskExecutor}.
 *
 * <p>Depuis ISSUE-150, {@link AssertionExecutor} etend {@link TaskExecutor}.
 * Toute implementation d'assertion est donc un {@code TaskExecutor} valide
 * et peut etre enregistree dans le {@code DefaultTaskExecutorRegistry}
 * (qui prend {@code List<TaskExecutor>}). Ce test le prouve en instanciant
 * les 6 vrais executors et en les passant directement au registre.
 *
 * <p>La collection automatique par Spring est testee separement dans
 * {@code AssertionExecutorsInTaskRegistryTest} du module
 * {@code platform-infrastructure}, qui utilise des stubs {@code TaskExecutor}
 * dans un contexte Spring.
 */
@DisplayName("AssertionExecutor as TaskExecutor — DefaultTaskExecutorRegistry compatibility")
class AssertionExecutorAsTaskExecutorTest {

    private DefaultTaskExecutorRegistry taskRegistry;

    @BeforeEach
    void setUp() {
        // ApplicationContext minimal (non-refresh) juste pour satisfaire
        // le constructeur de DatabaseAssertionExecutor. Le contexte n'est
        // pas utilise par getSupportedTaskName().
        GenericApplicationContext dummyContext = new GenericApplicationContext();

        List<TaskExecutor> executors = List.of(
                new GatlingMetricAssertionExecutor(),
                new DatabaseAssertionExecutor(dummyContext),
                new KafkaAssertionExecutor(),
                new WireMockAssertionExecutor(),
                new HttpMockAssertionExecutor(
                        new HttpTargetRegistry(Map.of(), RestClient.builder())),
                new FileAssertionExecutor());

        taskRegistry = new DefaultTaskExecutorRegistry(executors);
    }

    @Nested
    @DisplayName("TaskExecutorRegistry collection")
    class TaskExecutorRegistryCollection {

        @Test
        @DisplayName("should contain the 6 assertion names in getSupportedTaskNames()")
        void shouldContainAllSixAssertionNames() {
            Set<String> taskNames = taskRegistry.getSupportedTaskNames();

            assertThat(taskNames)
                    .containsExactlyInAnyOrder(
                            "gatling-metric",
                            "database-assertion",
                            "kafka",
                            "wiremock",
                            "http-mock",
                            "file");
        }

        @Test
        @DisplayName("should resolve gatling-metric via getFor()")
        void shouldResolveGatlingMetric() {
            TaskExecutor executor = taskRegistry.getFor("gatling-metric");
            assertThat(executor).isInstanceOf(GatlingMetricAssertionExecutor.class);
        }

        @Test
        @DisplayName("should resolve database-assertion via getFor()")
        void shouldResolveDatabaseAssertion() {
            TaskExecutor executor = taskRegistry.getFor("database-assertion");
            assertThat(executor).isInstanceOf(DatabaseAssertionExecutor.class);
        }

        @Test
        @DisplayName("should resolve kafka via getFor()")
        void shouldResolveKafka() {
            TaskExecutor executor = taskRegistry.getFor("kafka");
            assertThat(executor).isInstanceOf(KafkaAssertionExecutor.class);
        }

        @Test
        @DisplayName("should resolve wiremock via getFor()")
        void shouldResolveWiremock() {
            TaskExecutor executor = taskRegistry.getFor("wiremock");
            assertThat(executor).isInstanceOf(WireMockAssertionExecutor.class);
        }

        @Test
        @DisplayName("should resolve http-mock via getFor()")
        void shouldResolveHttpMock() {
            TaskExecutor executor = taskRegistry.getFor("http-mock");
            assertThat(executor).isInstanceOf(HttpMockAssertionExecutor.class);
        }

        @Test
        @DisplayName("should resolve file via getFor()")
        void shouldResolveFile() {
            TaskExecutor executor = taskRegistry.getFor("file");
            assertThat(executor).isInstanceOf(FileAssertionExecutor.class);
        }
    }

    @Nested
    @DisplayName("AssertionExecutorRegistry still resolves same executors")
    class AssertionExecutorRegistryStillWorks {

        @Test
        @DisplayName("should resolve all 6 executors via AssertionExecutorRegistry")
        void shouldResolveAllViaAssertionRegistry() {
            GenericApplicationContext dummyContext = new GenericApplicationContext();

            // AssertionExecutorRegistry is deprecated but must still work
            List<AssertionExecutor> executors = List.of(
                    new GatlingMetricAssertionExecutor(),
                    new DatabaseAssertionExecutor(dummyContext),
                    new KafkaAssertionExecutor(),
                    new WireMockAssertionExecutor(),
                    new HttpMockAssertionExecutor(
                            new HttpTargetRegistry(Map.of(), RestClient.builder())),
                    new FileAssertionExecutor());

            DefaultAssertionExecutorRegistry assertionRegistry =
                    new DefaultAssertionExecutorRegistry(executors);

            assertThat(assertionRegistry.getSupportedAssertionNames())
                    .containsExactlyInAnyOrder(
                            "gatling-metric", "database-assertion", "kafka",
                            "wiremock", "http-mock", "file");
        }
    }
}
