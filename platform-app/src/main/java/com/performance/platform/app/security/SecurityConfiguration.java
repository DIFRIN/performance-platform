package com.performance.platform.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de securite de la plateforme.
 * <p>
 * Deux modes selon la propriete {@code platform.security.enabled} :
 * <ul>
 *   <li><b>false</b> (defaut, mode LOCAL) : tout est autorise —
 *       pas d'authentification, pas d'autorisation.</li>
 *   <li><b>true</b> (mode DISTRIBUTED) : API REST securisee via OAuth2/JWT.
 *       Les endpoints {@code /api/**} necessitent un token JWT valide.
 *       Les endpoints {@code /actuator/health/**} restent publics pour les
 *       probes Kubernetes (CD-02).</li>
 * </ul>
 * <p>
 * L'authentification OAuth2/JWT est activee quand le resource server JWT
 * est configurable (propriete {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * ou {@code jwk-set-uri}). Si la securite est activee mais sans OAuth2 configure,
 * l'API rejette toutes les requetes vers {@code /api/**} — l'operateur
 * doit configurer le provider OAuth2.
 * <p>
 * CSRF desactive (API REST stateless). Sessions stateless (pas de JSESSIONID).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    static final String PROP_SECURITY_ENABLED = "platform.security.enabled";
    static final String PROP_OAUTH2_ISSUER = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    private final Environment environment;

    public SecurityConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Construit la chaine de filtres de securite.
     * <p>
     * <b>CC-02:</b> Pipeline cohesif — desactivation CSRF → configuration
     * sessions stateless → regles d'autorisation → activation OAuth2/JWT
     * conditionnelle. Les 4 etapes forment un flux de configuration
     * de securite atomique indissociable.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean securityEnabled = environment.getProperty(
                PROP_SECURITY_ENABLED, Boolean.class, false);

        // CSRF toujours desactive (API REST)
        http.csrf(AbstractHttpConfigurer::disable);

        if (!securityEnabled) {
            // Mode LOCAL — tout est autorise
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            log.info("action=security_disabled mode=permissive_all");
            return http.build();
        }

        // Mode DISTRIBUTED — API REST securisee
        http.sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                // Health probes Kubernetes (CD-02) — publiques
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // API REST — authentification requise
                .requestMatchers("/api/**").authenticated()
                // Tout le reste — autorise (swagger, assets, etc.)
                .anyRequest().permitAll()
        );

        // OAuth2/JWT — active uniquement si le resource server est configurable
        String issuerUri = environment.getProperty(PROP_OAUTH2_ISSUER);
        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(org.springframework.security.config.Customizer.withDefaults()));
            log.info("action=security_configured mechanism=OAUTH2_JWT issuer={}", issuerUri);
        } else {
            log.warn("action=security_misconfigured " +
                    "platform.security.enabled=true but no OAuth2 issuer configured. " +
                    "API endpoints will reject all requests. " +
                    "Set OAUTH2_ISSUER_URI or spring.security.oauth2.resourceserver.jwt.issuer-uri.");
        }

        return http.build();
    }
}
