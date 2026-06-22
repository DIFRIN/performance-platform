package com.performance.platform.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le chargement des profils de configuration Spring.
 * <p>
 * Chaque profil YAML ({@code application-{local,orchestrator,agent}.yaml})
 * definit les proprietes {@code runtime.mode}, {@code runtime.role},
 * {@code transport.type}, et {@code platform.security.enabled}
 * appropriees pour le mode de fonctionnement.
 * <p>
 * Utilise {@link YamlPropertySourceLoader} pour parser les fichiers YAML
 * sans charger le contexte Spring complet. Cela evite les problemes
 * d'auto-configuration avec les classes manquantes de Spring Boot 4.0.0
 * et l'incompatibilite JUnit 5.11.4 / @SpringBootTest.
 */
@DisplayName("ConfigProfiles")
class ConfigProfilesTest {

    // ---- Profil LOCAL ----

    @Nested
    @DisplayName("Profil LOCAL")
    class LocalProfileTest {

        @Test
        @DisplayName("should set runtime.mode=LOCAL")
        void shouldSetRuntimeModeLocal() throws IOException {
            var sources = loadYaml("application-local.yaml");
            assertThat(getProperty(sources, "runtime.mode")).isEqualTo("LOCAL");
        }

        @Test
        @DisplayName("should not set runtime.role (defaults to NONE)")
        void shouldDefaultRuntimeRole() throws IOException {
            var sources = loadYaml("application-local.yaml");
            assertThat(getProperty(sources, "runtime.role")).isNull();
        }

        @Test
        @DisplayName("should set transport.type=IN_MEMORY")
        void shouldSetTransportTypeInMemory() throws IOException {
            var sources = loadYaml("application-local.yaml");
            assertThat(getProperty(sources, "transport.type")).isEqualTo("IN_MEMORY");
        }

        @Test
        @DisplayName("should disable security")
        void shouldDisableSecurity() throws IOException {
            var sources = loadYaml("application-local.yaml");
            assertThat(getProperty(sources, "platform.security.enabled")).isEqualTo("false");
        }
    }

    // ---- Profil ORCHESTRATOR ----

    @Nested
    @DisplayName("Profil ORCHESTRATOR")
    class OrchestratorProfileTest {

        @Test
        @DisplayName("should set runtime.mode=DISTRIBUTED")
        void shouldSetRuntimeModeDistributed() throws IOException {
            var sources = loadYaml("application-orchestrator.yaml");
            assertThat(getProperty(sources, "runtime.mode")).isEqualTo("DISTRIBUTED");
        }

        @Test
        @DisplayName("should set runtime.role=ORCHESTRATOR")
        void shouldSetRuntimeRoleOrchestrator() throws IOException {
            var sources = loadYaml("application-orchestrator.yaml");
            assertThat(getProperty(sources, "runtime.role")).isEqualTo("ORCHESTRATOR");
        }

        @Test
        @DisplayName("should set transport.type=KAFKA")
        void shouldSetTransportTypeKafka() throws IOException {
            var sources = loadYaml("application-orchestrator.yaml");
            assertThat(getProperty(sources, "transport.type")).isEqualTo("KAFKA");
        }

        @Test
        @DisplayName("should enable security")
        void shouldEnableSecurity() throws IOException {
            var sources = loadYaml("application-orchestrator.yaml");
            assertThat(getProperty(sources, "platform.security.enabled")).isEqualTo("true");
        }
    }

    // ---- Profil AGENT ----

    @Nested
    @DisplayName("Profil AGENT")
    class AgentProfileTest {

        @Test
        @DisplayName("should set runtime.mode=DISTRIBUTED")
        void shouldSetRuntimeModeDistributed() throws IOException {
            var sources = loadYaml("application-agent.yaml");
            assertThat(getProperty(sources, "runtime.mode")).isEqualTo("DISTRIBUTED");
        }

        @Test
        @DisplayName("should set runtime.role=AGENT")
        void shouldSetRuntimeRoleAgent() throws IOException {
            var sources = loadYaml("application-agent.yaml");
            assertThat(getProperty(sources, "runtime.role")).isEqualTo("AGENT");
        }

        @Test
        @DisplayName("should set transport.type=KAFKA")
        void shouldSetTransportTypeKafka() throws IOException {
            var sources = loadYaml("application-agent.yaml");
            assertThat(getProperty(sources, "transport.type")).isEqualTo("KAFKA");
        }

        @Test
        @DisplayName("should enable security")
        void shouldEnableSecurity() throws IOException {
            var sources = loadYaml("application-agent.yaml");
            assertThat(getProperty(sources, "platform.security.enabled")).isEqualTo("true");
        }
    }

    // ---- Profil EXAMPLES-LOCAL (IoT scenarios) ----

    @Nested
    @DisplayName("Profil EXAMPLES-LOCAL")
    class ExamplesLocalProfileTest {

        @Test
        @DisplayName("should load application-examples-local.yaml from classpath")
        void shouldLoadExamplesLocalProfile() throws IOException {
            var sources = loadYaml("application-examples-local.yaml");
            assertThat(sources).isNotEmpty();
        }

        @Test
        @DisplayName("should contain kafka-clusters config")
        void shouldContainKafkaClustersConfig() throws IOException {
            var sources = loadYaml("application-examples-local.yaml");
            // Verify the YAML loads and contains expected top-level structures
            // by checking the raw source map (YamlPropertySourceLoader uses
            // flattened map with dot keys at the top level)
            boolean found = false;
            for (var ps : sources) {
                Object src = ps.getSource();
                if (src instanceof java.util.Map<?, ?> map) {
                    // Check for flattened keys containing kafka-clusters
                    for (Object key : map.keySet()) {
                        if (key.toString().contains("kafka-clusters")) {
                            found = true;
                            break;
                        }
                    }
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("should contain http-targets config")
        void shouldContainHttpTargetsConfig() throws IOException {
            var sources = loadYaml("application-examples-local.yaml");
            boolean found = false;
            for (var ps : sources) {
                Object src = ps.getSource();
                if (src instanceof java.util.Map<?, ?> map) {
                    for (Object key : map.keySet()) {
                        if (key.toString().contains("http-targets")) {
                            found = true;
                            break;
                        }
                    }
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("should not contain any GRPC reference")
        void shouldNotContainGrpc() throws IOException {
            var sources = loadYaml("application-examples-local.yaml");
            for (var ps : sources) {
                Object src = ps.getSource();
                if (src instanceof java.util.Map<?, ?> map) {
                    for (var entry : map.entrySet()) {
                        String key = entry.getKey().toString();
                        assertThat(key.toUpperCase()).doesNotContain("GRPC");
                    }
                }
            }
        }
    }

    // ---- Absence de references GRPC ----

    @Nested
    @DisplayName("Absence de references GRPC")
    class NoGrpcTest {

        @Test
        @DisplayName("transport.type should not be GRPC in any profile")
        void transportTypeShouldNotBeGrpc() throws IOException {
            for (String profileFile : new String[]{
                    "application-local.yaml",
                    "application-orchestrator.yaml",
                    "application-agent.yaml"
            }) {
                var sources = loadYaml(profileFile);
                String transportType = getProperty(sources, "transport.type");
                assertThat(transportType)
                        .as("transport.type in " + profileFile + " should not be GRPC")
                        .isNotEqualTo("GRPC");
            }
        }

        @Test
        @DisplayName("should not have any GRPC reference in any profile YAML")
        void shouldNotHaveGrpcPropertyInAnyProfile() throws IOException {
            for (String profileFile : new String[]{
                    "application-local.yaml",
                    "application-orchestrator.yaml",
                    "application-agent.yaml"
            }) {
                var sources = loadYaml(profileFile);
                for (PropertySource<?> ps : sources) {
                    Object source = ps.getSource();
                    if (source instanceof java.util.Map<?, ?> map) {
                        boolean foundGrpc = containsGrpc(map);
                        assertThat(foundGrpc)
                                .as("No GRPC reference in " + profileFile)
                                .isFalse();
                    }
                }
            }
        }
    }

    // ---- Helpers ----

    /**
     * Charge un fichier YAML comme liste de {@link PropertySource}.
     */
    private static List<PropertySource<?>> loadYaml(String filename) throws IOException {
        var loader = new YamlPropertySourceLoader();
        var resource = new ClassPathResource(filename);
        return loader.load(filename, resource);
    }

    /**
     * Recupere la valeur d'une propriete depuis un ensemble de PropertySources.
     */
    private static String getProperty(List<PropertySource<?>> sources, String key) {
        for (PropertySource<?> ps : sources) {
            Object value = ps.getProperty(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Verifie recursivement si une Map contient la chaine "GRPC"
     * dans ses cles ou valeurs.
     */
    private static boolean containsGrpc(java.util.Map<?, ?> map) {
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().toString().toUpperCase().contains("GRPC")) {
                return true;
            }
            if (entry.getValue() instanceof String str) {
                if (str.toUpperCase().contains("GRPC")) {
                    return true;
                }
            } else if (entry.getValue() instanceof java.util.Map<?, ?> nested) {
                if (containsGrpc(nested)) {
                    return true;
                }
            } else if (entry.getValue() instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof String str && str.toUpperCase().contains("GRPC")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
