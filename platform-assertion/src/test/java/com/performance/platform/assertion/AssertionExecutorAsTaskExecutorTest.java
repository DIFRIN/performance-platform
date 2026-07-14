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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'integration Spring verifiant que les 6 {@link AssertionExecutor}
 * sont automatiquement collectes par {@link DefaultTaskExecutorRegistry}
 * en tant que beans {@link TaskExecutor}. Depuis ISSUE-150,
 * {@link AssertionExecutor} etend {@link TaskExecutor}, donc Spring
 * injecte tous les beans d'assertion dans la liste {@code List<TaskExecutor>}.
 *
 * <p>Le Spring context produit les 6 vrais beans d'assertion. On les
 * collecte via {@code ApplicationContext.getBeansOfType(TaskExecutor.class)}
 * et on les passe a {@link DefaultTaskExecutorRegistry} — c'est le meme
 * mecanisme que le constructeur de {@link DefaultTaskExecutorRegistry}
 * en injection Spring directe (le constructeur prend exactement
 * {@code List<TaskExecutor>}).
 */
@DisplayName("AssertionExecutor as TaskExecutor -- Spring Bean Collection")
class AssertionExecutorAsTaskExecutorTest {

    private DefaultTaskExecutorRegistry taskRegistry;

    @BeforeEach
    void setUp() {
        // Creer un contexte Spring avec tous les vrais AssertionExecutor beans
        AnnotationConfigApplicationContext ctx = contextWithExecutors();

        // Collecter tous les TaskExecutor beans (inclut les AssertionExecutor)
        List<TaskExecutor> executors = ctx.getBeansOfType(TaskExecutor.class)
                .values().stream().toList();

        taskRegistry = new DefaultTaskExecutorRegistry(executors);

        ctx.close();
    }

    /**
     * Cree un Spring context minimal contenant les 6 vrais AssertionExecutor.
     * Utilise des @Bean explicites pour eviter le component-scanning qui
     * tirerait des dependances non resolues.
     */
    private static AnnotationConfigApplicationContext contextWithExecutors() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MinimalConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Configuration
    static class MinimalConfig {

        @Bean
        GatlingMetricAssertionExecutor gatlingMetricAssertionExecutor() {
            return new GatlingMetricAssertionExecutor();
        }

        @Bean
        DatabaseAssertionExecutor databaseAssertionExecutor() {
            // DatabaseAssertionExecutor a besoin d'un ApplicationContext.
            // Utilise une factory: le bean sera cree par le contexte lui-meme.
            // On ne peut pas injecter ApplicationContext dans un @Bean static,
            // donc on le cree manuellement via le contexte courant.
            // Pour eviter ce probleme, on fournit un contexte null (OK pour le test
            // de collection, car getSupportedTaskName() n'utilise pas le contexte).
            return new DatabaseAssertionExecutor(null);
        }

        @Bean
        KafkaAssertionExecutor kafkaAssertionExecutor() {
            return new KafkaAssertionExecutor();
        }

        @Bean
        WireMockAssertionExecutor wireMockAssertionExecutor() {
            return new WireMockAssertionExecutor();
        }

        @Bean
        FileAssertionExecutor fileAssertionExecutor() {
            return new FileAssertionExecutor();
        }

        @Bean
        HttpMockAssertionExecutor httpMockAssertionExecutor() {
            return new HttpMockAssertionExecutor(
                    new HttpTargetRegistry(Map.of(), RestClient.builder()));
        }
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
                            "database",
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
        @DisplayName("should resolve database via getFor()")
        void shouldResolveDatabase() {
            TaskExecutor executor = taskRegistry.getFor("database");
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
            // AssertionExecutorRegistry is deprecated but must still work
            List<AssertionExecutor> executors = List.of(
                    new GatlingMetricAssertionExecutor(),
                    new DatabaseAssertionExecutor(null),
                    new KafkaAssertionExecutor(),
                    new WireMockAssertionExecutor(),
                    new HttpMockAssertionExecutor(
                            new HttpTargetRegistry(Map.of(), RestClient.builder())),
                    new FileAssertionExecutor());

            DefaultAssertionExecutorRegistry assertionRegistry =
                    new DefaultAssertionExecutorRegistry(executors);

            assertThat(assertionRegistry.getSupportedAssertionNames())
                    .containsExactlyInAnyOrder(
                            "gatling-metric", "database", "kafka",
                            "wiremock", "http-mock", "file");
        }
    }
}
