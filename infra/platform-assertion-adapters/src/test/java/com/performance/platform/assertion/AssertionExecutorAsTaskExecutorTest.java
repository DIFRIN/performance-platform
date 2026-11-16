package com.performance.platform.assertion;

import com.performance.platform.assertion.direct.DirectAssertionAssertionExecutor;
import com.performance.platform.assertion.measure.DirectMeasurementResolver;
import com.performance.platform.assertion.performance.PerformanceThresholdsAssertionExecutor;
import com.performance.platform.infrastructure.executor.DefaultTaskExecutorRegistry;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que les assertions sont des {@link TaskExecutor} valides et qu'elles
 * sont resolues par le registre unifie.
 */
@DisplayName("AssertionExecutor as TaskExecutor")
class AssertionExecutorAsTaskExecutorTest {

    private DefaultTaskExecutorRegistry taskRegistry;

    @BeforeEach
    void setUp() {
        List<TaskExecutor> executors = List.of(
                new PerformanceThresholdsAssertionExecutor(),
                new DirectAssertionAssertionExecutor(
                        new DirectMeasurementResolver(emptyDataSource())));
        taskRegistry = new DefaultTaskExecutorRegistry(executors);
    }

    @Test
    @DisplayName("registers exactly the two assertion families")
    void shouldRegisterBothFamilies() {
        assertThat(taskRegistry.getSupportedTaskNames())
                .containsExactlyInAnyOrder("performance-thresholds", "direct-assertion");
    }

    @Test
    @DisplayName("resolves each family by task name")
    void shouldResolveByTaskName() {
        assertThat(taskRegistry.getFor("performance-thresholds"))
                .isInstanceOf(PerformanceThresholdsAssertionExecutor.class);
        assertThat(taskRegistry.getFor("direct-assertion"))
                .isInstanceOf(DirectAssertionAssertionExecutor.class);
    }

    @Test
    @DisplayName("declares the same name through both interfaces")
    void shouldDeclareSameName() {
        var executor = new PerformanceThresholdsAssertionExecutor();
        assertThat(executor.getSupportedAssertionName())
                .isEqualTo(executor.getSupportedTaskName());
    }

    private static ObjectProvider<DataSource> emptyDataSource() {
        return new ObjectProvider<>() {
            @Override
            public DataSource getObject() {
                return null;
            }

            @Override
            public DataSource getObject(Object... args) {
                return null;
            }

            @Override
            public DataSource getIfAvailable() {
                return null;
            }

            @Override
            public DataSource getIfUnique() {
                return null;
            }
        };
    }
}
