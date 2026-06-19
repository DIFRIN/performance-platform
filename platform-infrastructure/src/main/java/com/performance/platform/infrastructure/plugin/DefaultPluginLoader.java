package com.performance.platform.infrastructure.plugin;

import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Implementation par defaut de {@link PluginLoader}.
 * Scanne le repertoire de plugins, charge les JARs via {@link URLClassLoader},
 * scanne les annotations {@code @Preparation/@Injection/@Assertion}, et instancie
 * les {@link TaskExecutor} avec un constructeur no-arg.
 *
 * <p>Les erreurs sont non-bloquantes :
 * <ul>
 *   <li>JAR corrompu → {@link PluginError}, WARN log, passe au suivant</li>
 *   <li>Classe sans constructeur no-arg → {@link PluginError}, classe ignoree</li>
 *   <li>Aucun plugin trouve → INFO log, demarrage normal</li>
 *   <li>{@code platform.plugins.enabled=false} → chargeur court-circuite</li>
 * </ul>
 *
 * <p>Chargement one-shot au demarrage (CF-06), pas de hot-reload.</p>
 */
@Component
public class DefaultPluginLoader implements PluginLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginLoader.class);

    private final boolean pluginsEnabled;

    public DefaultPluginLoader(
            @Value("${platform.plugins.enabled:true}") boolean pluginsEnabled) {
        this.pluginsEnabled = pluginsEnabled;
    }

    /**
     * Scanne le repertoire de plugins, charge chaque JAR, et retourne le resultat
     * agrege. Methode orchestreur principale : validation, iteration sur les JARs,
     * delegation a {@link #loadExecutorsFromJar}, et agregation des metriques.
     *
     * <p><b>CC-02</b>: method ~62 lines — single orchestration flow (validate,
     * list, iterate, delegate, aggregate). Extracting sub-steps would scatter
     * the linear error-handling and metrics-accumulation logic.</p>
     */
    @Override
    public PluginLoadResult load(Path pluginDirectory) {
        if (pluginDirectory == null) {
            throw new NullPointerException("pluginDirectory must not be null");
        }
        if (!Files.isDirectory(pluginDirectory)) {
            throw new IllegalArgumentException("pluginDirectory must be an existing directory: " + pluginDirectory);
        }

        if (!pluginsEnabled) {
            log.info("action=plugins_disabled message=Plugin loading is disabled via platform.plugins.enabled=false");
            return new PluginLoadResult(0, 0, List.of(), List.of(), List.of());
        }

        log.info("action=plugin_loading_start pluginDirectory={}", pluginDirectory.toAbsolutePath());

        List<PluginWarning> allWarnings = new ArrayList<>();
        List<PluginError> allErrors = new ArrayList<>();
        int jarsLoaded = 0;
        int executorsRegistered = 0;

        List<Path> jarFiles = listJarFiles(pluginDirectory);
        if (jarFiles.isEmpty()) {
            log.info("action=no_plugins_found pluginDirectory={} message=No JAR files found, startup continues normally",
                    pluginDirectory.toAbsolutePath());
            return new PluginLoadResult(0, 0, List.of(), allWarnings, allErrors);
        }

        List<TaskExecutor> allExecutors = new ArrayList<>();

        for (Path jarPath : jarFiles) {
            String jarName = jarPath.getFileName().toString();
            log.info("action=loading_jar jar={}", jarName);

            try {
                List<TaskExecutor> executors = loadExecutorsFromJar(jarPath, allWarnings, allErrors);
                // JAR successfully opened and scanned — count it as loaded
                jarsLoaded++;
                executorsRegistered += executors.size();
                allExecutors.addAll(executors);
                if (executors.isEmpty() && !hasErrorsForJar(allErrors, jarName)) {
                    log.info("action=jar_no_executors jar={} message=No annotated TaskExecutor found", jarName);
                }
            } catch (IOException e) {
                // JAR could not be opened at all (corrupted ZIP, missing file, etc.)
                PluginError error = PluginError.of(jarName,
                        "Failed to open JAR: " + e.getMessage(), e);
                allErrors.add(error);
                log.warn("action=jar_open_failed jar={} message={}", jarName, e.getMessage(), e);
            } catch (Exception e) {
                // Unexpected error — still count as attempted but record error
                PluginError error = PluginError.of(jarName,
                        "Failed to process JAR: " + e.getMessage(), e);
                allErrors.add(error);
                log.warn("action=jar_load_failed jar={} message={}", jarName, e.getMessage(), e);
            }
        }

        PluginLoadResult result = new PluginLoadResult(jarsLoaded, executorsRegistered,
                allExecutors, allWarnings, allErrors);

        log.info("action=plugin_loading_complete jarsLoaded={} executorsRegistered={} warnings={} errors={}",
                jarsLoaded, executorsRegistered, allWarnings.size(), allErrors.size());

        return result;
    }

    /**
     * Liste tous les fichiers .jar dans le repertoire, tries par nom pour un
     * ordre de chargement deterministe.
     */
    private List<Path> listJarFiles(Path directory) {
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path entry : stream) {
                jars.add(entry);
            }
        } catch (IOException e) {
            log.warn("action=list_jars_failed directory={} message={}",
                    directory.toAbsolutePath(), e.getMessage(), e);
        }
        jars.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return jars;
    }

    /**
     * Charge les {@link TaskExecutor} depuis un fichier JAR.
     * Ouvre le JAR, itere sur les entrees .class, charge les classes,
     * verifie les annotations et le constructeur no-arg, puis instancie.
     *
     * <p><b>CC-02</b>: method ~47 lines — single cohesive I/O unit (open JAR,
     * create URLClassLoader, iterate entries, delegate to
     * {@link #tryLoadClass}, close resources). Extracting pieces would
     * complicate the try/finally cleanup of JarFile + URLClassLoader.</p>
     *
     * @throws IOException si le JAR ne peut pas etre ouvert (corrompu, permissions...)
     */
    private List<TaskExecutor> loadExecutorsFromJar(Path jarPath,
                                                     List<PluginWarning> warnings,
                                                     List<PluginError> errors) throws IOException {
        List<TaskExecutor> executors = new ArrayList<>();
        String jarName = jarPath.getFileName().toString();

        URLClassLoader classLoader = null;
        JarFile jarFile = null;

        try {
            jarFile = new JarFile(jarPath.toFile()); // throws IOException if corrupted
            URL[] urls = { jarPath.toUri().toURL() };
            // Use the platform classloader as parent so plugin classes can see the API
            classLoader = new URLClassLoader(urls, getClass().getClassLoader());

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Skip non-class files and module-info
                if (!entryName.endsWith(".class") || entryName.equals("module-info.class")) {
                    continue;
                }

                // Convert filesystem path to fully qualified class name
                String className = entryName
                        .replace('/', '.')
                        .replace('\\', '.')
                        .substring(0, entryName.length() - 6); // remove ".class"

                TaskExecutor executor = tryLoadClass(classLoader, className, jarName, warnings, errors);
                if (executor != null) {
                    executors.add(executor);
                    log.info("action=executor_loaded jar={} className={} taskName={}",
                            jarName, className, executor.getSupportedTaskName());
                }
            }
        } finally {
            // JarFile and URLClassLoader are closed separately — URLClassLoader
            // does not close the underlying JAR file.
            closeQuietly(jarFile, jarName);
            closeQuietly(classLoader, jarName);
        }

        return executors;
    }

    /**
     * Tente de charger et instancier une classe comme {@link TaskExecutor}.
     *
     * <p><b>CC-02</b>: method ~53 lines — single cohesive reflection pipeline
     * (load class → check annotations → check interface → find no-arg ctor →
     * instantiate). Each step builds on the previous; extracting would require
     * passing multiple intermediate results across methods.</p>
     *
     * @return l'instance de TaskExecutor, ou null si la classe n'est pas un executor valide
     */
    private TaskExecutor tryLoadClass(URLClassLoader classLoader, String className,
                                       String jarName,
                                       List<PluginWarning> warnings,
                                       List<PluginError> errors) {
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Skip silently — class may reference dependencies not in the JAR
            return null;
        } catch (Exception e) {
            errors.add(PluginError.of(jarName,
                    "Failed to load class " + className + ": " + e.getMessage(), e));
            return null;
        }

        // Check for at least one plugin annotation
        boolean isPreparation = clazz.isAnnotationPresent(Preparation.class);
        boolean isInjection = clazz.isAnnotationPresent(Injection.class);
        boolean isAssertion = clazz.isAnnotationPresent(Assertion.class);

        if (!isPreparation && !isInjection && !isAssertion) {
            // Not a plugin — skip silently (a JAR may contain many non-plugin classes)
            return null;
        }

        // Check it implements TaskExecutor
        if (!TaskExecutor.class.isAssignableFrom(clazz)) {
            warnings.add(new PluginWarning(jarName,
                    "Class " + className + " is annotated with @" +
                    annotationName(isPreparation, isInjection, isAssertion) +
                    " but does not implement TaskExecutor — skipping"));
            return null;
        }

        // Check for no-arg constructor and instantiate
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (TaskExecutor) ctor.newInstance();
        } catch (NoSuchMethodException e) {
            errors.add(PluginError.of(jarName,
                    "Class " + className + " has no no-arg constructor — cannot instantiate", e));
            log.warn("action=no_noarg_constructor jar={} className={}", jarName, className);
            return null;
        } catch (Exception e) {
            errors.add(PluginError.of(jarName,
                    "Failed to instantiate " + className + ": " + e.getMessage(), e));
            log.warn("action=instantiation_failed jar={} className={} message={}",
                    jarName, className, e.getMessage(), e);
            return null;
        }
    }

    private String annotationName(boolean prep, boolean inj, boolean asrt) {
        if (prep) return "Preparation";
        if (inj) return "Injection";
        if (asrt) return "Assertion";
        return "unknown";
    }

    /**
     * Verifie si des erreurs ont deja ete enregistrees pour un JAR donne.
     */
    private boolean hasErrorsForJar(List<PluginError> errors, String jarName) {
        return errors.stream().anyMatch(e -> e.pluginJar().equals(jarName));
    }

    private void closeQuietly(AutoCloseable resource, String jarName) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception e) {
            log.debug("action=close_resource_failed jar={} message={}", jarName, e.getMessage());
        }
    }
}
