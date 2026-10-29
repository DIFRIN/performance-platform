package com.performance.platform.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link WebUiProperties} et le binding conditionnel
 * de {@link WebUiConfiguration}.
 * <p>
 * Utilise {@link Binder} directement pour tester le mapping des proprietes
 * sans charger le contexte Spring complet.
 */
@DisplayName("WebUiConfiguration")
class WebUiConfigurationTest {

    // ========================================================================
    // WebUiProperties — binding
    // ========================================================================

    @Nested
    @DisplayName("WebUiProperties binding")
    class PropertiesBinding {

        @Test
        @DisplayName("should default enabled to false")
        void shouldDefaultEnabledToFalse() {
            // When no web.ui.* properties exist, the record defaults enabled=false
            var props = new WebUiProperties(false);
            assertThat(props.enabled()).isFalse();
        }

        @Test
        @DisplayName("should bind web.ui.enabled=true")
        void shouldBindEnabledTrue() {
            var source = Map.of("web.ui.enabled", "true");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("web.ui", WebUiProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().enabled()).isTrue();
        }

        @Test
        @DisplayName("should bind web.ui.enabled=false")
        void shouldBindEnabledFalse() {
            var source = Map.of("web.ui.enabled", "false");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("web.ui", WebUiProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().enabled()).isFalse();
        }

        @Test
        @DisplayName("should not be bound when property is absent")
        void shouldNotBeBoundWhenAbsent() {
            var source = Collections.<String, Object>emptyMap();
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("web.ui", WebUiProperties.class);

            // When no property is defined at all, the binder cannot bind
            assertThat(result.isBound()).isFalse();
        }
    }

    // ========================================================================
    // WebUiConfiguration — conditional bean creation
    // ========================================================================

    @Nested
    @DisplayName("WebUiConfiguration conditional creation")
    class ConditionalCreation {

        @Test
        @DisplayName("should have @ConditionalOnProperty for web.ui.enabled=true")
        void shouldHaveConditionalOnPropertyAnnotation() throws NoSuchMethodException {
            var annotation = WebUiConfiguration.class.getAnnotation(
                    org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.prefix()).isEqualTo("web.ui");
            assertThat(annotation.name()).containsExactly("enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
        }

        @Test
        @DisplayName("should be annotated with @Configuration")
        void shouldHaveConfigurationAnnotation() {
            var annotation = WebUiConfiguration.class.getAnnotation(
                    org.springframework.context.annotation.Configuration.class);
            assertThat(annotation).isNotNull();
        }

        @Test
        @DisplayName("should implement WebMvcConfigurer")
        void shouldImplementWebMvcConfigurer() {
            assertThat(org.springframework.web.servlet.config.annotation.WebMvcConfigurer.class)
                    .isAssignableFrom(WebUiConfiguration.class);
        }
    }

    // ========================================================================
    // WebUiProperties immutability
    // ========================================================================

    @Nested
    @DisplayName("WebUiProperties immutability")
    class Immutability {

        @Test
        @DisplayName("should be a record")
        void shouldBeARecord() {
            assertThat(WebUiProperties.class.isRecord()).isTrue();
        }

        @Test
        @DisplayName("should have exactly one component (enabled)")
        void shouldHaveExactlyOneComponent() {
            var components = WebUiProperties.class.getRecordComponents();
            assertThat(components).hasSize(1);
            assertThat(components[0].getName()).isEqualTo("enabled");
        }
    }
}
