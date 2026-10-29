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

    @Test
    void onlyReportsAllowSameOriginFramingInBothSecurityModes() throws Exception {
        for (boolean securityEnabled : List.of(false, true)) {
            try (var context = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext()) {
                context.setServletContext(new MockServletContext());
                context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.MapPropertySource("test",
                                java.util.Map.of(SecurityConfiguration.PROP_SECURITY_ENABLED, securityEnabled)));
                context.register(SecurityConfiguration.class);
                context.refresh();
                var security = context.getBean("springSecurityFilterChain", Filter.class);
                for (String path : List.of("/api/v1/executions/exec-1/report", "/index.html",
                        "/api/v1/executions/exec-1/tasks")) {
                    var request = new MockHttpServletRequest("GET", path);
                    request.setServletPath(path);
                    var response = new MockHttpServletResponse();
                    security.doFilter(request, response, new MockFilterChain());
                    assertThat(response.getHeader("X-Frame-Options"))
                            .isEqualTo(path.endsWith("/report") ? "SAMEORIGIN" : "DENY");
                    if (securityEnabled && path.startsWith("/api/")) {
                        assertThat(response.getStatus()).isIn(401, 403);
                    }
                }
            }
        }
    }

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

        @Test
        @DisplayName("API_ROLES should require USER or ADMIN on /api/**")
        void shouldDocumentApiRoles() {
            assertThat(SecurityConfiguration.API_ROLES)
                    .containsExactly("USER", "ADMIN");
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
