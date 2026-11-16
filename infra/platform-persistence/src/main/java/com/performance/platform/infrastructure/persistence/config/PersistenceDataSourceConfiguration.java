package com.performance.platform.infrastructure.persistence.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Primary {@link DataSource} of the platform store, built from
 * {@code platform.datasources.default.*} (ADR-014).
 * <p>
 * Owned by {@code platform-persistence} (ADR-057): the store does not depend on
 * the database executor module. The bean is named {@code dataSource} so that
 * {@link JpaConfiguration} (activated by the same property) and
 * Flyway pick it up. Without {@code platform.datasources.default.url} (AGENT
 * mode) the bean, and therefore JPA, is not created.
 *
 * @see ADR-025
 */
@Configuration
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
@EnableConfigurationProperties(PersistenceDataSourceConfiguration.StoreDatasourceProperties.class)
public class PersistenceDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PersistenceDataSourceConfiguration.class);

    @Bean
    @Primary
    public DataSource dataSource(StoreDatasourceProperties props) {
        var hikari = new HikariDataSource();
        hikari.setPoolName("hikari-default");
        hikari.setJdbcUrl(props.url());
        hikari.setUsername(props.username());
        hikari.setPassword(props.password());
        if (props.driverClassName() != null) {
            hikari.setDriverClassName(props.driverClassName());
        }
        var pool = props.hikari();
        if (pool != null) {
            if (pool.maximumPoolSize() != null) {
                hikari.setMaximumPoolSize(pool.maximumPoolSize());
            }
            if (pool.minimumIdle() != null) {
                hikari.setMinimumIdle(pool.minimumIdle());
            }
            if (pool.connectionTimeout() != null) {
                hikari.setConnectionTimeout(pool.connectionTimeout());
            }
        }
        log.info("action=primary_datasource url={}", props.url());
        return hikari;
    }

    /** Binding of {@code platform.datasources.default.*}; same keys as any named datasource. */
    @ConfigurationProperties(prefix = "platform.datasources.default")
    public record StoreDatasourceProperties(
            String url,
            String username,
            String password,
            String driverClassName,
            Pool hikari
    ) {
        public record Pool(Integer maximumPoolSize, Integer minimumIdle, Long connectionTimeout) {}
    }
}
