package com.performance.platform.arch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification des frontieres inter-modules Maven (T1).
 * <p>
 * ArchUnit 1.3/1.4 ne sait pas lire le bytecode Java 25 (major 69) : il
 * importe silencieusement zero classe. Ce test lit donc les fichiers
 * {@code .class} via l'ASM repackage de Spring (qui supporte Java 25) et
 * verifie les references effectives entre packages de modules.
 * <p>
 * Les classes de test sont ignorees (seuls {@code target/classes} et les
 * jars de modules sont scannes).
 */
@DisplayName("Module Dependencies — frontieres inter-modules")
class ModuleDependencyTest {

    private static final String ENGINE = "com/performance/platform/engine/";
    private static final String ASSERTION = "com/performance/platform/assertion/";
    private static final String AGENT = "com/performance/platform/agent/";
    private static final String REPORTING = "com/performance/platform/reporting/";
    private static final String INJECTION = "com/performance/platform/injection/";
    private static final String INFRASTRUCTURE = "com/performance/platform/infrastructure/";

    /** internal class name -> referenced internal class names. */
    private static Map<String, Set<String>> dependencies;

    @BeforeAll
    static void scanModules() throws IOException {
        dependencies = scanPlatformClasses();
    }

    @Test
    @DisplayName("le scanner voit reellement les classes des modules")
    void scannerMustSeeEveryModule() {
        assertThat(dependencies.keySet())
                .anyMatch(c -> c.startsWith(ENGINE))
                .anyMatch(c -> c.startsWith(ASSERTION))
                .anyMatch(c -> c.startsWith(AGENT))
                .anyMatch(c -> c.startsWith(REPORTING))
                .anyMatch(c -> c.startsWith(INJECTION))
                .anyMatch(c -> c.startsWith(INFRASTRUCTURE));
    }

    @Test
    @DisplayName("engine ne depend pas de assertion")
    void engineMustNotDependOnAssertion() {
        assertNoDependency(ENGINE, ASSERTION, Set.of(),
                "le moteur resout les assertions via le registre unifie (PDR-035)");
    }

    @Test
    @DisplayName("engine ne depend pas de agent-runtime")
    void engineMustNotDependOnAgentRuntime() {
        assertNoDependency(ENGINE, AGENT, Set.of(),
                "le dispatch lifecycle passe par application.ports.out.LifecycleSignalHandler");
    }

    @Test
    @DisplayName("reporting ne depend pas de injection-gatling")
    void reportingMustNotDependOnInjection() {
        assertNoDependency(REPORTING, INJECTION, Set.of(),
                "le reporting consomme les records du domaine");
    }

    @Test
    @DisplayName("assertion ne depend pas de injection-gatling")
    void assertionMustNotDependOnInjection() {
        assertNoDependency(ASSERTION, INJECTION, Set.of(),
                "GatlingMetricAssertionExecutor utilise domain.injection.InjectionResult");
    }

    @Test
    @DisplayName("assertion ne depend de infrastructure que pour HttpMockAssertionExecutor")
    void assertionMustNotDependOnInfrastructureExceptHttpMock() {
        assertNoDependency(ASSERTION, INFRASTRUCTURE,
                Set.of(ASSERTION + "httpmock/HttpMockAssertionExecutor"),
                "seule l'assertion HTTP a besoin de HttpTargetRegistry "
                        + "(exception documentee, candidate a une extraction)");
    }

    // ------------------------------------------------------------------
    // Rule engine
    // ------------------------------------------------------------------

    private static void assertNoDependency(String fromPrefix, String toPrefix,
                                           Set<String> exemptSources, String because) {
        List<String> violations = new ArrayList<>();
        dependencies.forEach((source, refs) -> {
            if (!source.startsWith(fromPrefix) || exemptSources.contains(source)) {
                return;
            }
            for (String ref : refs) {
                if (ref.startsWith(toPrefix)) {
                    violations.add(source + " -> " + ref);
                }
            }
        });

        if (!violations.isEmpty()) {
            throw new AssertionError("Frontiere violee (" + because + "):\n  "
                    + violations.stream().sorted().distinct().limit(50)
                            .collect(Collectors.joining("\n  ")));
        }
    }

    // ------------------------------------------------------------------
    // Scanner
    // ------------------------------------------------------------------

    private static Map<String, Set<String>> scanPlatformClasses() throws IOException {
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path", ""));

        List<Path> locations = Stream.of(classpath.split(File.pathSeparator))
                .filter(entry -> entry.contains(File.separator + "platform-"))
                .map(Path::of)
                .filter(Files::exists)
                .filter(ModuleDependencyTest::isMainClassesLocation)
                // Les repertoires target/classes priment sur les jars installes.
                .sorted(Comparator.comparing(p -> !p.toString().endsWith("target/classes")))
                .toList();

        Map<String, Set<String>> result = new TreeMap<>();
        for (Path location : locations) {
            if (Files.isDirectory(location)) {
                scanDirectory(location, result);
            } else if (location.toString().endsWith(".jar")) {
                scanJar(location, result);
            }
        }
        return result;
    }

    private static boolean isMainClassesLocation(Path path) {
        String value = path.toString();
        return value.endsWith("target/classes") || value.endsWith(".jar");
    }

    private static void scanDirectory(Path root, Map<String, Set<String>> result) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                addClass(bytes, result);
            }
        }
    }

    private static void scanJar(Path jarPath, Map<String, Set<String>> result) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().contains("module-info")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    addClass(in.readAllBytes(), result);
                }
            }
        }
    }

    private static void addClass(byte[] bytes, Map<String, Set<String>> result) {
        try {
            ClassReader reader = new ClassReader(bytes);
            result.putIfAbsent(reader.getClassName(), references(reader));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot read class file: " + e.getMessage(), e);
        }
    }

    private static Set<String> references(ClassReader reader) {
        Set<String> refs = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                add(superName, refs);
                if (interfaces != null) {
                    for (String itf : interfaces) {
                        add(itf, refs);
                    }
                }
            }

            @Override
            public org.springframework.asm.FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                addDescriptor(descriptor, refs);
                return null;
            }

            @Override
            public org.springframework.asm.MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                addDescriptor(descriptor, refs);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        add(exception, refs);
                    }
                }
                return new org.springframework.asm.MethodVisitor(Opcodes.ASM9) {

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        add(type, refs);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner,
                                               String name, String descriptor) {
                        add(owner, refs);
                        addDescriptor(descriptor, refs);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        add(owner, refs);
                        addDescriptor(descriptor, refs);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor,
                                                       Handle bootstrapMethodHandle,
                                                       Object... bootstrapMethodArguments) {
                        addDescriptor(descriptor, refs);
                        addHandle(bootstrapMethodHandle, refs);
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Type type) {
                                addType(type, refs);
                            } else if (argument instanceof Handle handle) {
                                addHandle(handle, refs);
                            }
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type type) {
                            addType(type, refs);
                        } else if (value instanceof Handle handle) {
                            addHandle(handle, refs);
                        }
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        addDescriptor(descriptor, refs);
                    }
                };
            }

            @Override
            public org.springframework.asm.AnnotationVisitor visitAnnotation(
                    String descriptor, boolean visible) {
                addDescriptor(descriptor, refs);
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return refs;
    }

    private static void addHandle(Handle handle, Set<String> refs) {
        if (handle == null) {
            return;
        }
        add(handle.getOwner(), refs);
        addDescriptor(handle.getDesc(), refs);
    }

    private static void addDescriptor(String descriptor, Set<String> refs) {
        if (descriptor == null || descriptor.isBlank()) {
            return;
        }
        if (descriptor.startsWith("(")) {
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                addType(argument, refs);
            }
            addType(Type.getReturnType(descriptor), refs);
        } else {
            addType(Type.getType(descriptor), refs);
        }
    }

    private static void addType(Type type, Set<String> refs) {
        Type current = type;
        while (current.getSort() == Type.ARRAY) {
            current = current.getElementType();
        }
        if (current.getSort() == Type.OBJECT) {
            add(current.getInternalName(), refs);
        }
    }

    private static void add(String internalName, Set<String> refs) {
        if (internalName != null && !internalName.isBlank()) {
            refs.add(internalName);
        }
    }
}
