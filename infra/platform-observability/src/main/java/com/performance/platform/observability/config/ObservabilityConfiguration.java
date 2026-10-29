package com.performance.platform.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * Configuration de l'observabilite plateforme.
 * <p>
 * Centralise les reglages Micrometer et prepare les points d'extension
 * pour OpenTelemetry (tracer, spans). Les beans definis ici sont
 * conditionnels a la presence du {@link MeterRegistry} sur le classpath
 * (apporte par {@code micrometer-core}).
 * <p>
 * <b>Personnalisations :</b>
 * <ul>
 *   <li>Tag commun {@code service=performance-platform} sur toutes les metriques</li>
 *   <li>Filtre rejetant les metriques Spring internes ({@code spring.*})</li>
 * </ul>
 * <p>
 * <b>Extension OTel :</b> Quand {@code micrometer-tracing} et
 * {@code opentelemetry} seront sur le classpath, ajouter ici un
 * {@code @Bean ObservationRegistry} ou {@code @Bean Tracer}.
 */
@Configuration
public class ObservabilityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfiguration.class);

    static final String TAG_SERVICE = "service";
    static final String SERVICE_NAME = "performance-platform";

    private final MeterRegistry meterRegistry;

    public ObservabilityConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry,
                "meterRegistry must not be null");
    }

    /**
     * Personnalise le {@link MeterRegistry} avec les tags et filtres communs.
     * <p>
     * <b>CC-02:</b> Pipeline cohesif de configuration du registry —
     * ajout du tag service commun → filtrage des metriques internes
     * Spring → denominateurs de percentile. Les 3 etapes forment
     * un flux de configuration atomique indissociable.
     */
    @Bean
    public MeterRegistryMdcConfigurer meterRegistryMdcConfigurer() {
        return new MeterRegistryMdcConfigurer(meterRegistry);
    }

    /**
     * Applique tags communs et filtres au {@link MeterRegistry} via callback.
     * Classe statique pour eviter la pollution du scope public.
     */
    static class MeterRegistryMdcConfigurer {

        MeterRegistryMdcConfigurer(MeterRegistry registry) {
            registry.config()
                    .commonTags(TAG_SERVICE, SERVICE_NAME)
                    .meterFilter(MeterFilter.denyNameStartsWith("spring."));
            log.info("action=configure_meter_registry service={} filters=spring.*", SERVICE_NAME);
        }
    }
}
