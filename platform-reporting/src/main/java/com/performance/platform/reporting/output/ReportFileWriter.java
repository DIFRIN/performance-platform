package com.performance.platform.reporting.output;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.InjectionReportEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ecrit les rapports de campagne sur disque.
 * <p>
 * Structure de sortie :
 * <pre>
 * {@code <outputDirectory>/<executionId>/
 *   campaign.html
 *   campaign.pdf
 *   campaign.json
 *   gatling/<simulationClass>/
 * }
 * </pre>
 * <p>
 * Seuls les formats configures via {@code reporting.formats} sont produits.
 * La copie des repertoires Gatling est parallelisee via Virtual Threads.
 * <p>
 * CC-02 : le pipeline d'ecriture (resolve base dir → render formats →
 * copy Gatling dirs) forme un ensemble cohesif indissociable.
 */
@Component
public class ReportFileWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportFileWriter.class);

    private static final List<ReportFormat> DEFAULT_FORMATS =
            List.of(ReportFormat.HTML, ReportFormat.PDF, ReportFormat.JSON);

    // ── Constantes de nommage (CRAFT-08) ──
    private static final String DEFAULT_OUTPUT_DIRECTORY = "reports";
    private static final String REPORT_FILE_PREFIX = "campaign.";
    private static final String GATLING_SUBDIR = "gatling";

    private final Map<ReportFormat, ReportRenderer> rendererByFormat;
    private final String outputDirectory;
    private final List<ReportFormat> formats;

    /**
     * Construit le writer avec les renderers disponibles et la configuration.
     *
     * @param renderers la liste des renderers disponibles (injectee par Spring)
     * @param props     les proprietes de configuration (injectees par Spring)
     */
    public ReportFileWriter(List<ReportRenderer> renderers, ReportProperties props) {
        this.rendererByFormat = List.copyOf(renderers).stream()
                .collect(Collectors.toUnmodifiableMap(
                        ReportRenderer::getFormat, Function.identity()));
        this.outputDirectory = props.outputDirectory() != null
                ? props.outputDirectory() : DEFAULT_OUTPUT_DIRECTORY;
        this.formats = props.formats() != null && !props.formats().isEmpty()
                ? List.copyOf(props.formats()) : DEFAULT_FORMATS;
    }

    /**
     * Ecrit le rapport de campagne dans le repertoire de sortie pour l'execution donnee.
     *
     * @param executionId l'identifiant d'execution
     * @param report      le rapport de campagne a ecrire
     * @return le chemin du repertoire de sortie ({@code <outputDirectory>/<executionId>/})
     */
    public Path write(ExecutionId executionId, CampaignReport report) {
        var execId = executionId.value();
        log.info("action=write_report executionId={} reportId={}",
                execId, report.id().value());

        try {
            Path baseDir = Path.of(outputDirectory, execId);
            Files.createDirectories(baseDir);

            // Ecrire le rapport dans chaque format configure
            writeRenderOutputs(report, baseDir, execId);

            // Copier les repertoires Gatling (I/O sous Virtual Threads)
            copyGatlingDirectories(report, baseDir, execId);

            log.info("action=write_report_complete executionId={} baseDir={}",
                    execId, baseDir);
            return baseDir;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write report for executionId=" + execId, e);
        }
    }

    // ──────────────────────────────────────────────
    // Render outputs
    // ──────────────────────────────────────────────

    private void writeRenderOutputs(CampaignReport report, Path baseDir,
                                     String executionId) throws IOException {
        for (ReportFormat format : formats) {
            ReportRenderer renderer = rendererByFormat.get(format);
            if (renderer == null) {
                log.warn("action=no_renderer_for_format executionId={} format={}",
                        executionId, format);
                continue;
            }
            byte[] content = renderer.render(report);
            Path filePath = baseDir.resolve(REPORT_FILE_PREFIX + formatExtension(format));
            Files.write(filePath, content);
            log.info("action=format_written executionId={} format={} path={} size={}",
                    executionId, format, filePath, content.length);
        }
    }

    // ──────────────────────────────────────────────
    // Gatling directory copy (Virtual Threads)
    // ──────────────────────────────────────────────

    private void copyGatlingDirectories(CampaignReport report, Path baseDir,
                                        String executionId) {
        List<InjectionReportEntry> entries = report.injectionResults();
        if (entries.isEmpty()) {
            return;
        }

        Path gatlingBase = baseDir.resolve(GATLING_SUBDIR);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Void>>();

            for (InjectionReportEntry entry : entries) {
                Path sourceDir = entry.gatlingReportDirectory();
                if (sourceDir == null || !Files.exists(sourceDir)) {
                    log.warn("action=gatling_dir_missing executionId={} simClass={}",
                            executionId, entry.metrics().simulationClass());
                    continue;
                }
                String simName = sanitizeFilename(entry.metrics().simulationClass());
                Path destDir = gatlingBase.resolve(simName);

                String execId = executionId;
                futures.add(executor.submit(() -> {
                    copyDirectory(sourceDir, destDir);
                    log.info("action=gatling_copied executionId={} simName={} dest={}",
                            execId, simName, destDir);
                    return null;
                }));
            }

            // Attendre la fin de toutes les copies Gatling
            for (Future<Void> f : futures) {
                f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("action=gatling_copy_interrupted executionId={}", executionId, e);
        } catch (ExecutionException e) {
            log.error("action=gatling_copy_failed executionId={} cause={}",
                    executionId, String.valueOf(e.getCause()));
        }
    }

    /**
     * Copie recursive d'un repertoire.
     * CC-02 : le flux walk→createDirectories→copy forme un pipeline cohesif
     * de copie de fichiers insécable.
     */
    private void copyDirectory(Path source, Path dest) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path destPath = dest.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to copy " + sourcePath, e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause() instanceof IOException ioe ? ioe : new IOException(e.getCause());
        }
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /** Nettoie un nom de classe pour l'utiliser comme nom de repertoire. */
    static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Retourne l'extension de fichier correspondant au format (minuscule). */
    static String formatExtension(ReportFormat format) {
        return format.name().toLowerCase();
    }
}
