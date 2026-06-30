package com.performance.platform.infrastructure.persistence.config;

import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Manual JPA configuration for Spring Boot 4.0.0 which removed
 * {@code JpaRepositoriesAutoConfiguration} / {@code HibernateJpaAutoConfiguration}.
 * <p>
 * Enables Spring Data JPA repositories under the {@code persistence} package,
 * creates an {@link EntityManagerFactory} scanning JPA entities, and sets up
 * the transaction manager.
 * <p>
 * Entire configuration (and all its beans) is skipped when no
 * {@link DataSource} bean is present — e.g. AGENT mode without a configured
 * datasource. {@code @ConditionalOnBean} is preferred here because it
 * naturally mirrors the datasource activation: if {@code DatasourceConfiguration}
 * creates a {@code DataSource}, JPA follows; otherwise nothing is wired.
 *
 * @see ADR-025
 */
@Configuration
@ConditionalOnBean(DataSource.class)
@EnableJpaRepositories(basePackages = "com.performance.platform.infrastructure.persistence")
@EnableTransactionManagement
public class JpaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JpaConfiguration.class);

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.performance.platform.infrastructure.persistence");
        emf.setPersistenceUnitName("performance-platform");

        var vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(false);
        emf.setJpaVendorAdapter(vendorAdapter);

        // Hibernate EntityManagerFactory also implements SessionFactory;
        // Spring ORM needs this hint to avoid interface conflict detection.
        emf.setEntityManagerFactoryInterface(jakarta.persistence.EntityManagerFactory.class);

        log.info("action=jpa_entity_manager_factory packages=com.performance.platform.infrastructure.persistence");
        return emf;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        var txManager = new JpaTransactionManager(emf);
        log.info("action=jpa_transaction_manager");
        return txManager;
    }
}
