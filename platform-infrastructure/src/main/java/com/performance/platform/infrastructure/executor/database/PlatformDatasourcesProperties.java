package com.performance.platform.infrastructure.executor.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for named datasources under the {@code platform.datasources} prefix.
 * <p>
 * Each entry defines the JDBC connection parameters and optional HikariCP pool settings
 * for a logical datasource name referenced by {@link DatabaseTaskExecutor} steps.
 * <p>
 * Credentials use the {@code ${ENV_VAR:default}} convention (ADR-006) — never hardcoded.
 *
 * <h3>YAML example</h3>
 * <pre>{@code
 * platform:
 *   datasources:
 *     customer-db:
 *       url: jdbc:postgresql://localhost:5432/customers
 *       username: ${DB_USER:postgres}
 *       password: ${DB_PASSWORD:changeme}
 *       driver-class-name: org.postgresql.Driver
 *       hikari:
 *         maximum-pool-size: 10
 *         minimum-idle: 2
 *         connection-timeout: 30000
 * }</pre>
 *
 * @see DatasourceConfiguration
 * @see ADR-014
 */
@ConfigurationProperties(prefix = "platform")
public record PlatformDatasourcesProperties(
        Map<String, DatasourceProperties> datasources
) {

    /**
     * JDBC connection parameters for a single named datasource.
     */
    public record DatasourceProperties(
            String url,
            String username,
            String password,
            String driverClassName,
            HikariProperties hikari
    ) {}

    /**
     * Optional HikariCP pool tuning parameters. All fields nullable — absent values
     * fall back to HikariCP defaults.
     */
    public record HikariProperties(
            Integer maximumPoolSize,
            Integer minimumIdle,
            Long connectionTimeout
    ) {}
}
