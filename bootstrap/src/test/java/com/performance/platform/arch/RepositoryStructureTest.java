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
        assertThat(root.resolve("distribution/examples/sut/device-api/pom.xml")).isRegularFile();
        assertThat(root.resolve("distribution/examples/scenarios/device-api-local.yaml")).isRegularFile();
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
