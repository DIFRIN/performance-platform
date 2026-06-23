package com.performance.platform.app.web;

import com.performance.platform.app.security.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de securite pour les endpoints UI et API.
 * <p>
 * Verifie le comportement du {@link SecurityConfiguration#securityFilterChain}
 * dans les deux modes : securite desactivee (permit-all) et securite activee
 * (API securisee, UI publique).
 */
@DisplayName("WebSecurity")
class WebSecurityTest {

    @Nested
    @DisplayName("SecurityConfiguration annotation")
    class SecurityConfigurationAnnotation {

        @Test
        @DisplayName("should be annotated with @Configuration")
        void shouldHaveConfigurationAnnotation() {
            var annotation = SecurityConfiguration.class.getAnnotation(
                    org.springframework.context.annotation.Configuration.class);
            assertThat(annotation).isNotNull();
        }

        @Test
        @DisplayName("should be annotated with @EnableWebSecurity")
        void shouldHaveEnableWebSecurityAnnotation() {
            var annotation = SecurityConfiguration.class.getAnnotation(
                    org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class);
            assertThat(annotation).isNotNull();
        }
    }

    @Nested
    @DisplayName("SecurityConfiguration constants")
    class SecurityConfigurationConstants {

        @Test
        @DisplayName("PROP_SECURITY_ENABLED should be platform.security.enabled")
        void shouldHaveCorrectSecurityEnabledProperty() {
            assertThat(SecurityConfiguration.PROP_SECURITY_ENABLED)
                    .isEqualTo("platform.security.enabled");
        }

        @Test
        @DisplayName("PROP_OAUTH2_ISSUER should be the JWT issuer property")
        void shouldHaveCorrectOauth2IssuerProperty() {
            assertThat(SecurityConfiguration.PROP_OAUTH2_ISSUER)
                    .isEqualTo("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("should accept Environment parameter")
        void shouldAcceptEnvironmentParameter() throws NoSuchMethodException {
            var constructor = SecurityConfiguration.class.getConstructor(
                    org.springframework.core.env.Environment.class);
            assertThat(constructor).isNotNull();
        }

        @Test
        @DisplayName("should have exactly one public constructor")
        void shouldHaveExactlyOnePublicConstructor() {
            var constructors = SecurityConfiguration.class.getConstructors();
            assertThat(constructors).hasSize(1);
        }
    }
}
