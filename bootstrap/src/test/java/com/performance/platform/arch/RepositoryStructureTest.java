package com.performance.platform.arch;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryStructureTest {

    @Test
    void reactorModulesAndParentsResolveFromTheirNewLocations() throws Exception {
        Path root = repositoryRoot();
        var modules = readPom(root.resolve("pom.xml")).getElementsByTagName("module");
        assertThat(modules.getLength()).isEqualTo(20);
        for (int i = 0; i < modules.getLength(); i++) {
            Path directory = root.resolve(modules.item(i).getTextContent());
            assertThat(directory.resolve("pom.xml")).isRegularFile();
            Element pom = readPom(directory.resolve("pom.xml"));
            var parents = pom.getElementsByTagName("parent");
            Element parent = (Element) parents.item(0);
            var paths = parent.getElementsByTagName("relativePath");
            String relativePath = paths.getLength() == 0 ? "../pom.xml" : paths.item(0).getTextContent();
            assertThat(directory.resolve(relativePath).normalize()).isEqualTo(root.resolve("pom.xml"));
        }
    }

    @Test
    void executionProductionDependenciesAreLimitedToCoreContracts() throws Exception {
        Path root = repositoryRoot();
        var allowed = Set.of("platform-domain", "platform-use-cases", "platform-plugin-api",
                "platform-transport-api");
        for (String module : Set.of("platform-execution-engine", "platform-agent-runtime")) {
            var dependencies = readPom(root.resolve("execution").resolve(module).resolve("pom.xml"))
                    .getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                var scopes = dependency.getElementsByTagName("scope");
                if (scopes.getLength() > 0 && "test".equals(scopes.item(0).getTextContent())) {
                    continue;
                }
                if ("com.performance".equals(dependency.getElementsByTagName("groupId").item(0).getTextContent())) {
                    assertThat(dependency.getElementsByTagName("artifactId").item(0).getTextContent())
                            .isIn(allowed);
                }
            }
        }
    }

    @Test
    void projectKnowledgeIsIndependentOfTheHarness() {
        Path root = repositoryRoot();
        assertThat(root.resolve("docs/knowledge/architecture.md")).isRegularFile();
        assertThat(root.resolve(".claude/knowledge")).doesNotExist();
        assertThat(root.resolve(".claude/skills/testing-strategy/SKILL.md")).isRegularFile();
        assertThat(root.resolve("distribution/example/sut/pom.xml")).isRegularFile();
        assertThat(root.resolve("distribution/example/scenarios/01-kafka-to-http-local.yaml")).isRegularFile();
        assertThat(root.resolve("distribution/example/MODOP.md")).isRegularFile();
    }

    @Test
    void dockerBuildCopiesEveryReactorModuleFromExistingLocations() throws Exception {
        Path root = repositoryRoot();
        var lines = Files.readAllLines(root.resolve("distribution/docker/Dockerfile"));
        var modules = readPom(root.resolve("pom.xml")).getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            String module = modules.item(i).getTextContent();
            assertThat(lines).contains("COPY " + module + "/pom.xml " + module + "/pom.xml");
        }
        for (String line : lines) {
            if (line.startsWith("COPY ") && !line.startsWith("COPY --from=")) {
                String source = line.split("\\s+")[1];
                assertThat(root.resolve(source)).exists();
            }
        }
        assertThat(lines).contains("COPY --from=build /app/bootstrap/target/performance-platform.jar app.jar");
    }

    @Test
    void composeBuildContextsAndDockerfilesResolve() throws Exception {
        Path root = repositoryRoot();
        try (var files = Files.walk(root.resolve("distribution"))) {
            for (Path compose : files.filter(p -> p.getFileName().toString().startsWith("docker-compose")
                    && p.toString().endsWith(".yaml")).toList()) {
                try (var input = Files.newInputStream(compose)) {
                    Object document = new Yaml().load(input);
                    assertThat(document).isInstanceOf(Map.class);
                    if (!(document instanceof Map<?, ?> configuration)
                            || !(configuration.get("services") instanceof Map<?, ?> services)) {
                        throw new AssertionError("Missing Compose services: " + compose);
                    }
                    for (Object value : services.values()) {
                        if (value instanceof Map<?, ?> service && service.get("build") instanceof Map<?, ?> build) {
                            Path context = compose.getParent().resolve(build.get("context").toString()).normalize();
                            assertThat(context).isDirectory();
                            String dockerfile = build.get("dockerfile") instanceof String name ? name : "Dockerfile";
                            assertThat(context.resolve(dockerfile)).isRegularFile();
                        }
                    }
                }
            }
        }
    }

    @Test
    void reportVolumesAreInitializedBeforeNonRootReportProducersStart() throws Exception {
        Path root = repositoryRoot();
        assertThat(Files.readString(root.resolve("distribution/docker/Dockerfile"))).contains("USER perf");
        for (String mode : Set.of("local", "distributed")) {
            try (var input = Files.newInputStream(root.resolve(
                    "distribution/example/docker-compose-" + mode + ".yaml"))) {
                Map<?, ?> document = new Yaml().load(input);
                Map<?, ?> services = (Map<?, ?>) document.get("services");
                Map<?, ?> initializer = (Map<?, ?>) services.get("reports-init");
                assertThat(initializer.get("user")).isEqualTo("0:0");
                assertThat(initializer.get("restart")).isEqualTo("no");
                assertThat(initializer.get("network_mode")).isEqualTo("none");
                assertThat(initializer.get("volumes")).isEqualTo(java.util.List.of("./reports:/reports"));
                assertThat(initializer.get("entrypoint")).isEqualTo(java.util.List.of("sh", "-ec",
                        "chown perf:perf /reports; chmod u+rwx /reports; su -s /bin/sh perf -c 'test -w /reports'"));
                Map<?, ?> healthcheck = (Map<?, ?>) initializer.get("healthcheck");
                assertThat(healthcheck.get("disable")).isEqualTo(true);
                int reportProducers = 0;
                for (Object value : services.values()) {
                    if (value instanceof Map<?, ?> service
                            && service.get("environment") instanceof Map<?, ?> environment
                            && "/reports".equals(environment.get("REPORTING_OUTPUT_DIR"))) {
                        assertThat(service.containsKey("user")).isFalse();
                        assertThat(service.get("image")).isEqualTo(initializer.get("image"));
                        assertThat(service.get("volumes")).isInstanceOf(java.util.List.class);
                        assertThat(((java.util.List<?>) service.get("volumes")).contains("./reports:/reports")).isTrue();
                        Map<?, ?> dependencies = (Map<?, ?>) service.get("depends_on");
                        Map<?, ?> initialization = (Map<?, ?>) dependencies.get("reports-init");
                        assertThat(initialization.get("condition")).isEqualTo("service_completed_successfully");
                        reportProducers++;
                    }
                }
                assertThat(reportProducers).isPositive();
            }
        }
    }

    @Test
    void alpineTlsBypassIsConditionalAndLimitedToTheBuilder() throws Exception {
        String dockerfile = Files.readString(repositoryRoot().resolve("distribution/docker/Dockerfile"))
                .replace("\r\n", "\n");
        String builder = dockerfile.substring(0, dockerfile.lastIndexOf("\nFROM "));
        String runtime = dockerfile.substring(dockerfile.lastIndexOf("\nFROM "));
        assertThat(builder).contains("ARG MAVEN_TLS_INSECURE=false");
        assertThat(builder).contains("""
                RUN if [ "$MAVEN_TLS_INSECURE" = "true" ]; then \\
                      echo "WARNING: TLS certificate verification disabled for build dependency downloads" >&2; \\
                      apk --no-check-certificate add --no-cache maven; \\
                    else \\
                      apk add --no-cache maven; \\
                    fi""");
        assertThat(runtime).doesNotContain("--no-check-certificate", "MAVEN_TLS_INSECURE");
    }

    @Test
    void mavenTlsBypassIsOptInAndCoversBothImageBuilds() throws Exception {
        Path root = repositoryRoot();
        for (String file : Set.of("distribution/docker/Dockerfile", "distribution/example/sut/Dockerfile")) {
            String dockerfile = Files.readString(root.resolve(file));
            assertThat(dockerfile).contains("ARG MAVEN_TLS_INSECURE=false");
            String builder = dockerfile.substring(0, dockerfile.lastIndexOf("\nFROM "));
            String runtime = dockerfile.substring(dockerfile.lastIndexOf("\nFROM "));
            assertThat(runtime).doesNotContain("MAVEN_TLS_INSECURE", "maven.wagon.http.ssl");
            var commands = builder.replace("\\\r\n", "").replace("\\\n", "").lines()
                    .filter(line -> line.startsWith("RUN mvn ")).toList();
            assertThat(commands).hasSize(file.contains("/sut/") ? 2 : 1);
            for (String command : commands) {
                assertThat(command).contains(
                        "-Dmaven.resolver.transport=wagon",
                        "-Dmaven.wagon.http.ssl.insecure=${MAVEN_TLS_INSECURE}",
                        "-Dmaven.wagon.http.ssl.allowall=${MAVEN_TLS_INSECURE}");
            }
        }
        for (String mode : Set.of("local", "distributed")) {
            try (var input = Files.newInputStream(root.resolve(
                    "distribution/example/docker-compose-" + mode + ".yaml"))) {
                Map<?, ?> document = new Yaml().load(input);
                Map<?, ?> services = (Map<?, ?>) document.get("services");
                int imageBuilds = 0;
                for (Object value : services.values()) {
                    if (value instanceof Map<?, ?> service && service.get("build") instanceof Map<?, ?> build) {
                        assertThat(build.get("args")).isInstanceOf(Map.class);
                        Map<?, ?> args = (Map<?, ?>) build.get("args");
                        assertThat(args.get("MAVEN_TLS_INSECURE")).isEqualTo("${MAVEN_TLS_INSECURE:-false}");
                        imageBuilds++;
                    }
                }
                assertThat(imageBuilds).isGreaterThanOrEqualTo(2);
            }
        }
    }

    private static Element readPom(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(input).getDocumentElement();
        }
    }

    private static Path repositoryRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate = directory; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("core"))
                    && Files.isDirectory(candidate.resolve("bootstrap"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate repository root from " + directory);
    }
}
