package com.performance.platform.infrastructure.executor.database;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Creates a {@link DatasourceProvider} bean populated from {@code platform.datasources.*}
 * configuration properties.
 * <p>
 * Each named datasource entry is materialized as a {@link HikariDataSource} with its
 * own connection pool, then registered in the provider by logical name. Adding a new
 * datasource requires zero code — just a new YAML entry under {@code platform.datasources}.
 * <p>
 * {@link DatasourceProvider} is <strong>no longer</strong> annotated with
 * {@code @Component}; this configuration class is its single source of truth.
 *
 * @see ADR-014
 */
@Configuration
@EnableConfigurationProperties(PlatformDatasourcesProperties.class)
public class DatasourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfiguration.class);

    @Bean
    public DatasourceProvider datasourceProvider(PlatformDatasourcesProperties props) {
        var provider = new DatasourceProvider();
        if (props.datasources() == null || props.datasources().isEmpty()) {
            log.warn("action=no_datasource_configured prefix=platform.datasources");
            return provider;
        }
        props.datasources().forEach((name, ds) -> {
            var hikari = buildHikari(name, ds);
            provider.register(name, hikari);
            log.info("action=datasource_configured name={} url={}", name, ds.url());
        });
        return provider;
    }

    /**
     * Exposes the {@code "default"} datasource as a Spring {@link DataSource} bean,
     * satisfying {@code @ConditionalOnBean(DataSource.class)} for JPA auto-configuration
     * (ADR-014). The bean is named {@code dataSource} so Spring Boot and Flyway
     * auto-configuration pick it up correctly.
     * <p>
     * Conditional on {@code platform.datasources.default.url}: when no default datasource
     * is configured (AGENT mode, etc.), this bean is simply not created — no exception thrown.
     *
     * @see ADR-025
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
    public DataSource dataSource(PlatformDatasourcesProperties props) {
        if (props.datasources() == null || !props.datasources().containsKey("default")) {
            log.warn("action=no_default_datasource — JPA auto-configuration will be skipped");
            return null; // unreachable when ConditionalOnProperty matches, safe guard
        }
        var ds = props.datasources().get("default");
        var hikari = buildHikari("default", ds);
        log.info("action=primary_datasource url={}", ds.url());
        return hikari;
    }

    private HikariDataSource buildHikari(String name, PlatformDatasourcesProperties.DatasourceProperties ds) {
        var hikari = new HikariDataSource();
        hikari.setPoolName("hikari-" + name);
        hikari.setJdbcUrl(ds.url());
        hikari.setUsername(ds.username());
        hikari.setPassword(ds.password());
        if (ds.driverClassName() != null) {
            hikari.setDriverClassName(ds.driverClassName());
        }
        if (ds.hikari() != null) {
            if (ds.hikari().maximumPoolSize() != null) {
                hikari.setMaximumPoolSize(ds.hikari().maximumPoolSize());
            }
            if (ds.hikari().minimumIdle() != null) {
                hikari.setMinimumIdle(ds.hikari().minimumIdle());
            }
            if (ds.hikari().connectionTimeout() != null) {
                hikari.setConnectionTimeout(ds.hikari().connectionTimeout());
            }
        }
        return hikari;
    }
}
