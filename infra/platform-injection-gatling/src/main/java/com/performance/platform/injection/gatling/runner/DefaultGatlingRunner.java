package com.performance.platform.injection.gatling.runner;

import com.performance.platform.injection.gatling.load.LoadModelTranslator;

import io.gatling.javaapi.core.OpenInjectionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation par defaut de {@link GatlingRunner}.
 * <p>
 * Lance une simulation Gatling in-process sur un Virtual Thread avec
 * timeout. Les etapes d'injection sont traduites du {@code LoadModel}
 * via le {@code LoadModelTranslator} et rendues disponibles a la
 * simulation via {@link SimulationInjectionHolder}.
 * <p>
 * <strong>Lancement Gatling</strong> : utilise {@link EmbeddedGatlingLauncher},
 * equivalent embarque de {@code Gatling$.MODULE$.fromArgs(String[])} (memes
 * arguments CLI, pas de {@code System.exit()}) qui, contrairement a
 * {@code fromArgs}, n'arrete pas le {@code LoggerContext} Logback de
 * l'application hote en fin de run.
 * <p>
 * <strong>Virtual Threads</strong> : le lancement Gatling est un appel
 * bloquant (demarre Akka, Netty). L'execution est soumise a un
 * {@code Executors.newVirtualThreadPerTaskExecutor()} avec timeout.
 */
@Component
public class DefaultGatlingRunner implements GatlingRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultGatlingRunner.class);

    private static final String SIM_ID_PROPERTY = "gatling.simulationId";

    private final LoadModelTranslator translator;

    public DefaultGatlingRunner(LoadModelTranslator translator) {
        this.translator = translator;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>CC-02 (taille &gt; 40 lignes) :</strong> la methode depasse 40 lignes
     * car elle execute un pipeline sequentiel coherent de preparation et lancement
     * Gatling avec gestion d'erreurs (timeout, interruption, echec). Les etapes
     * (traduction, injection holder, proprietes, args CLI, execution, nettoyage)
     * sont trop interdependantes pour etre extraites de maniere autonome sans
     * introduire de complexite artificielle a travers l'etat partage
     * ({@code config}, {@code steps}, proprietes systeme).
     */
    @Override
    public Path run(GatlingRunConfig config) {
        log.info("action=gatling_run simulationId={} simulationClass={} timeout={}",
                config.simulationId(), config.simulationClass(), config.timeout());

        // 1. Traduire LoadModel en etapes d'injection Gatling
        List<OpenInjectionStep> steps = translator.translate(config.loadModel());
        log.debug("action=load_model_translated simulationId={} stepCount={}",
                config.simulationId(), steps.size());

        // 2. Rendre les etapes disponibles a la simulation
        SimulationInjectionHolder.set(config.simulationId(), steps);

        // 3. Sauvegarder et appliquer les proprietes systeme
        Map<String, String> previousSysProps = new HashMap<>();
        try {
            if (!config.systemProperties().isEmpty()) {
                config.systemProperties().forEach((k, v) -> {
                    var old = System.getProperty(k);
                    if (old != null) previousSysProps.put(k, old);
                    System.setProperty(k, v);
                });
            }
            var oldSimId = System.setProperty(SIM_ID_PROPERTY, config.simulationId());
            if (oldSimId != null) previousSysProps.put(SIM_ID_PROPERTY, oldSimId);

            // 4. Construire les arguments CLI Gatling
            String[] gatlingArgs = buildGatlingArgs(config);

            // 5. Lancer Gatling sur un Virtual Thread avec timeout
            Path resultsDir = executeWithTimeout(gatlingArgs, config);

            log.info("action=gatling_run_complete simulationId={} resultsDir={}",
                    config.simulationId(), resultsDir);
            return resultsDir;

        } catch (TimeoutException e) {
            throw new GatlingExecutionException(
                    "Gatling simulation timed out after " + config.timeout() +
                    " for simulationId=" + config.simulationId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatlingExecutionException(
                    "Gatling simulation interrupted for simulationId=" +
                    config.simulationId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new GatlingExecutionException(
                    "Gatling simulation failed for simulationId=" +
                    config.simulationId() + ": " + cause.getMessage(), cause);
        } finally {
            // 6. Nettoyer
            SimulationInjectionHolder.remove(config.simulationId());
            // Restaurer les proprietes systeme
            previousSysProps.forEach((k, v) -> {
                if (v != null) System.setProperty(k, v);
                else System.clearProperty(k);
            });
            // Nettoyer les proprietes qui n'avaient pas de valeur precedente
            config.systemProperties().forEach((k, v) -> {
                if (!previousSysProps.containsKey(k)) System.clearProperty(k);
            });
            if (!previousSysProps.containsKey(SIM_ID_PROPERTY)) {
                System.clearProperty(SIM_ID_PROPERTY);
            }
        }
    }

    /**
     * Construit les arguments CLI pour Gatling.
     * Format : {@code -s <simulationClass> -rf <resultsDir> [-rd <runDescription>]}
     */
    private String[] buildGatlingArgs(GatlingRunConfig config) {
        List<String> args = new ArrayList<>();
        args.add("-s");
        args.add(config.simulationClass());
        args.add("-rf");
        args.add(config.resultsDirectory().toAbsolutePath().toString());
        args.add("-nr"); // no reports — evite le besoin de ComponentLibrary SPI
        if (config.simulationId() != null && !config.simulationId().isBlank()) {
            args.add("-rd");
            args.add(config.simulationId());
        }
        log.debug("action=gatling_args args={}", args);
        return args.toArray(new String[0]);
    }

    /**
     * Execute Gatling sur un Virtual Thread avec timeout.
     */
    private Path executeWithTimeout(String[] gatlingArgs,
                                     GatlingRunConfig config)
            throws TimeoutException, InterruptedException, ExecutionException {

        Set<Path> previousDirectories;
        try {
            Files.createDirectories(config.resultsDirectory());
            try (var entries = Files.list(config.resultsDirectory())) {
                previousDirectories = entries.filter(Files::isDirectory)
                        .map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toSet());
            }
        } catch (IOException e) {
            throw new GatlingExecutionException("Cannot prepare results directory " + config.resultsDirectory(), e);
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> future = executor.submit(() ->
                    EmbeddedGatlingLauncher.launch(gatlingArgs));

            int exitCode;
            long timeoutMs = config.timeout().toMillis();
            if (timeoutMs > 0) {
                exitCode = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                exitCode = future.get();
            }

            log.debug("action=gatling_exit_code simulationId={} exitCode={}",
                    config.simulationId(), exitCode);

            return findResultsDirectory(config.resultsDirectory(),
                    previousDirectories);
        }
    }

    /**
     * Trouve le repertoire de resultats Gatling genere.
     * Selects only the new child containing this run's log, never a stale run or the base directory.
     */
    Path findResultsDirectory(Path baseDir, Set<Path> previousDirectories) {
        try (var entries = Files.list(baseDir)) {
            List<Path> candidates = entries
                    .filter(Files::isDirectory)
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> !previousDirectories.contains(p))
                    .filter(p -> Files.isRegularFile(p.resolve("simulation.log")))
                    .toList();
            if (candidates.size() != 1) {
                throw new GatlingExecutionException(
                        "Expected one new Gatling results directory in " + baseDir
                                + ", found " + candidates.size());
            }
            return candidates.getFirst();
        } catch (IOException e) {
            throw new GatlingExecutionException("Cannot read results directory " + baseDir, e);
        }
    }
}
