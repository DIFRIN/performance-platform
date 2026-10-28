package com.performance.platform.assertion.database;

import com.performance.platform.infrastructure.executor.database.DatasourceConfiguration;
import com.performance.platform.infrastructure.executor.database.DatasourceProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseAssertionExecutorTest {

    @Test
    void doesNotHideAnInvalidNamedBeanAsAMissingDatasource() {
        try (var context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("sut-db", "invalid datasource bean");
            context.refresh();

            assertThatThrownBy(() -> new DatabaseAssertionExecutor(context).resolveDataSource("sut-db"))
                    .isInstanceOf(org.springframework.beans.factory.BeanNotOfRequiredTypeException.class);
        }
    }

    @Test
    void resolvesConfiguredLogicalDatasourceWithoutANamedSpringBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "platform.datasources.sut-db.url", "jdbc:postgresql://localhost:5433/sut_devices",
                    "platform.datasources.sut-db.username", "postgres",
                    "platform.datasources.sut-db.password", "test",
                    "platform.datasources.sut-db.driver-class-name", "org.postgresql.Driver")));
            context.register(DatasourceConfiguration.class);
            context.refresh();
            var provider = context.getBean(DatasourceProvider.class);
            var datasource = (HikariDataSource) provider.get("sut-db");
            try {
                assertThat(context.containsBean("sut-db")).isFalse();
                assertThat(new DatabaseAssertionExecutor(context).resolveDataSource("sut-db"))
                        .isSameAs(datasource);
                assertThat(new DatabaseAssertionExecutor(context).resolveDataSource("unknown")).isNull();
            } finally {
                datasource.close();
            }
        }
    }

    @Test
    void preservesNamedBeanAndDefaultAliasWithoutUsingPrimaryForUnknownNames() {
        try (var context = new GenericApplicationContext()) {
            var primary = new PGSimpleDataSource();
            context.getBeanFactory().registerSingleton("dataSource", primary);
            context.refresh();
            var executor = new DatabaseAssertionExecutor(context);

            assertThat(executor.resolveDataSource("dataSource")).isSameAs(primary);
            assertThat(executor.resolveDataSource("default")).isSameAs(primary);
            assertThat(executor.resolveDataSource("sut-db")).isNull();
        }
    }

    @Test
    void configuredLogicalResourceTakesPrecedenceOverSameNamedBean() {
        try (var context = new GenericApplicationContext()) {
            var configured = new PGSimpleDataSource();
            var provider = new DatasourceProvider();
            provider.register("sut-db", configured);
            context.getBeanFactory().registerSingleton("provider", provider);
            context.getBeanFactory().registerSingleton("sut-db", new PGSimpleDataSource());
            context.refresh();

            assertThat(new DatabaseAssertionExecutor(context).resolveDataSource("sut-db"))
                    .isSameAs(configured);
        }
    }
}
