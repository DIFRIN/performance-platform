package com.performance.platform.infrastructure.executor;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'integration Spring verifiant que {@link DefaultTaskExecutorRegistry}
 * collecte automatiquement les beans {@link TaskExecutor} via injection
 * Spring {@code List<TaskExecutor>}.
 * <p>
 * Ce mecanisme de collection automatique est le meme pour les
 * {@code AssertionExecutor} beans (depuis ISSUE-150, {@code AssertionExecutor}
 * etend {@code TaskExecutor}). Les 6 implementations d'assertion du module
 * {@code platform-assertion} sont donc automatiquement enregistrees dans
 * le {@code DefaultTaskExecutorRegistry} de production.
 */
@DisplayName("Spring auto-collection of TaskExecutor beans in DefaultTaskExecutorRegistry")
class AssertionExecutorsInTaskRegistryTest {

    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * Stub TaskExecutor representant un executor d'assertion (comme le seraient
     * les beans AssertionExecutor dans le contexte de production).
     * <p>
     * N'utilise pas Mockito pour eviter les incompatibilites ByteBuddy/Java 25.
     */
    private static TaskExecutor stubExecutor(String taskName) {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext context, StepDefinition step) {
                return TaskResult.success(new TaskId("stub"), taskName, Duration.ZERO, Map.of());
            }

            @Override
            public String getSupportedTaskName() {
                return taskName;
            }
        };
    }

    @Configuration
    static class TestConfig {

        @Bean
        TaskExecutor databaseAssertion() {
            return stubExecutor("database");
        }

        @Bean
        TaskExecutor gatlingMetricAssertion() {
            return stubExecutor("gatling-metric");
        }

        @Bean
        TaskExecutor kafkaAssertion() {
            return stubExecutor("kafka");
        }

        @Bean
        TaskExecutor wiremockAssertion() {
            return stubExecutor("wiremock");
        }

        @Bean
        TaskExecutor httpMockAssertion() {
            return stubExecutor("http-mock");
        }

        @Bean
        TaskExecutor fileAssertion() {
            return stubExecutor("file");
        }

        @Bean
        DefaultTaskExecutorRegistry defaultTaskExecutorRegistry(List<TaskExecutor> executors) {
            return new DefaultTaskExecutorRegistry(executors);
        }
    }

    @Nested
    @DisplayName("Auto-collection via Spring")
    class AutoCollectionViaSpring {

        @Test
        @DisplayName("should collect all 6 TaskExecutor beans from the context")
        void shouldCollectAllSixTaskExecutorBeans() {
            DefaultTaskExecutorRegistry registry =
                    context.getBean(DefaultTaskExecutorRegistry.class);
            Set<String> taskNames = registry.getSupportedTaskNames();

            assertThat(taskNames)
                    .containsExactlyInAnyOrder(
                            "database", "gatling-metric", "kafka",
                            "wiremock", "http-mock", "file");
        }

        @Test
        @DisplayName("should resolve database by taskName")
        void shouldResolveDatabaseByName() {
            DefaultTaskExecutorRegistry registry =
                    context.getBean(DefaultTaskExecutorRegistry.class);

            TaskExecutor executor = registry.getFor("database");

            assertThat(executor).isNotNull();
            assertThat(executor.getSupportedTaskName()).isEqualTo("database");
        }

        @Test
        @DisplayName("should resolve gatling-metric by taskName")
        void shouldResolveGatlingMetricByName() {
            DefaultTaskExecutorRegistry registry =
                    context.getBean(DefaultTaskExecutorRegistry.class);

            TaskExecutor executor = registry.getFor("gatling-metric");

            assertThat(executor).isNotNull();
            assertThat(executor.getSupportedTaskName()).isEqualTo("gatling-metric");
        }
    }

    @Nested
    @DisplayName("getSupportedTaskNames()")
    class GetSupportedTaskNames {

        @Test
        @DisplayName("should return exactly 6 task names")
        void shouldReturnExactlySixTaskNames() {
            DefaultTaskExecutorRegistry registry =
                    context.getBean(DefaultTaskExecutorRegistry.class);

            assertThat(registry.getSupportedTaskNames()).hasSize(6);
        }

        @Test
        @DisplayName("should return all 6 assertion-style names")
        void shouldReturnAllSixAssertionNames() {
            DefaultTaskExecutorRegistry registry =
                    context.getBean(DefaultTaskExecutorRegistry.class);

            assertThat(registry.getSupportedTaskNames())
                    .contains("database")
                    .contains("gatling-metric")
                    .contains("kafka")
                    .contains("wiremock")
                    .contains("http-mock")
                    .contains("file");
        }
    }
}
