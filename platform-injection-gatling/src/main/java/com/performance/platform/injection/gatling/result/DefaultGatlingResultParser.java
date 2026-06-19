package com.performance.platform.injection.gatling.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation par defaut de {@link GatlingResultParser}.
 * <p>
 * Lit le fichier {@code stats.json} genere par Gatling 3.x et extrait
 * les metriques globales : compteurs de requetes, latences (p50/p75/p95/p99),
 * throughput, taux d'erreur.
 * <p>
 * <strong>Format attendu :</strong> Gatling 3.x {@code stats.json} avec
 * un tableau {@code "stats"} dont la premiere entree est le groupe
 * {@code "Global"} (ou une map {@code "contents"} avec cle globale).
 * <p>
 * <strong>Mapping des champs Gatling :</strong>
 * <ul>
 *   <li>{@code numberOfRequests.total} → totalRequests</li>
 *   <li>{@code numberOfRequests.ok} → successfulRequests</li>
 *   <li>{@code numberOfRequests.ko} → failedRequests</li>
 *   <li>{@code meanNumberOfRequestsPerSecond.total} → throughput</li>
 *   <li>{@code minResponseTime.total} → minMs</li>
 *   <li>{@code maxResponseTime.total} → maxMs</li>
 *   <li>{@code meanResponseTime.total} → meanMs</li>
 *   <li>{@code percentiles1.total} → p50Ms</li>
 *   <li>{@code percentiles2.total} → p75Ms</li>
 *   <li>{@code percentiles3.total} → p95Ms</li>
 *   <li>{@code percentiles4.total} → p99Ms</li>
 * </ul>
 */
@Component
public class DefaultGatlingResultParser implements GatlingResultParser {

    private static final Logger log = LoggerFactory.getLogger(DefaultGatlingResultParser.class);

    private static final String STATS_JSON = "stats.json";
    private static final String JS_STATS_JSON = "js/stats.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Pipeline de parsing en 5 etapes sequentielles coherentes
     * ({@code find → read → extract → build rawStats → assemble}). CC-02 :
     * l'extraction en sous-methodes artificielles nuirait a la lisibilite
     * (toutes les metriques dependent du meme {@code globalStats}, le
     * pipeline est purement sequentiel sans branchement conditionnel).
     *
     * @see #findStatsFile(Path)
     * @see #readGlobalStats(Path)
     */
    @Override
    public InjectionResult parse(Path gatlingResultDirectory, TaskId taskId)
            throws ResultParsingException {

        log.info("action=parse_results taskId={} directory={}",
                taskId.value(), gatlingResultDirectory);

        // 1. Trouver stats.json
        Path statsFile = findStatsFile(gatlingResultDirectory);

        // 2. Lire et parser le JSON
        JsonNode globalStats = readGlobalStats(statsFile);

        // 3. Extraire les metriques
        var nbRequests = requireNode(globalStats, "numberOfRequests");
        long totalRequests = requireLong(nbRequests, "total");
        long successfulRequests = requireLong(nbRequests, "ok");
        long failedRequests = requireLong(nbRequests, "ko");

        double throughput = requireDouble(
                requireNode(globalStats, "meanNumberOfRequestsPerSecond"), "total");

        long minMs = requireLong(requireNode(globalStats, "minResponseTime"), "total");
        long maxMs = requireLong(requireNode(globalStats, "maxResponseTime"), "total");
        double meanMs = requireDouble(requireNode(globalStats, "meanResponseTime"), "total");

        long p50Ms = requireLong(requireNode(globalStats, "percentiles1"), "total");
        long p75Ms = requireLong(requireNode(globalStats, "percentiles2"), "total");
        long p95Ms = requireLong(requireNode(globalStats, "percentiles3"), "total");
        long p99Ms = requireLong(requireNode(globalStats, "percentiles4"), "total");
        // Gatling ne fournit pas p90 nativement → interpolation lineaire p75-p95
        long p90Ms = p75Ms + Math.round((p95Ms - p75Ms) * 0.75);

        double errorRate = InjectionResult.computeErrorRate(totalRequests, failedRequests);

        // 4. Construire rawStats (copie brute pour assertions custom)
        //    Conversion type-safe : objects → Map, arrays → List, scalaires → valeur native
        Map<String, Object> rawStats = new LinkedHashMap<>();
        globalStats.fieldNames().forEachRemaining(field -> {
            JsonNode value = globalStats.get(field);
            if (value.isObject()) {
                rawStats.put(field, objectMapper.convertValue(value, Map.class));
            } else if (value.isArray()) {
                rawStats.put(field, objectMapper.convertValue(value, List.class));
            } else if (value.isNumber()) {
                rawStats.put(field, value.numberValue());
            } else if (value.isBoolean()) {
                rawStats.put(field, value.booleanValue());
            } else {
                rawStats.put(field, value.asText());
            }
        });

        log.info("action=parse_results_complete taskId={} totalRequests={} errorRate={}% throughput={}",
                taskId.value(), totalRequests, String.format("%.2f", errorRate), throughput);

        // 5. Construire InjectionResult
        // Duration n'est pas dans stats.json → 0 pour l'instant
        // (sera complete par GatlingTaskExecutor ISSUE-057)
        return new InjectionResult(
                taskId,
                "Gatling", // simulationClass non disponible dans stats.json seul
                Duration.ZERO,
                totalRequests,
                successfulRequests,
                failedRequests,
                errorRate,
                throughput,
                p50Ms, p75Ms, p90Ms, p95Ms, p99Ms,
                maxMs, minMs, meanMs,
                gatlingResultDirectory.toAbsolutePath().normalize(),
                Map.copyOf(rawStats)
        );
    }

    /**
     * Trouve le fichier stats.json dans le repertoire de resultats.
     * Cherche d'abord dans {@code js/stats.json} puis a la racine.
     */
    private Path findStatsFile(Path directory) {
        Path jsStats = directory.resolve(JS_STATS_JSON);
        if (Files.exists(jsStats)) {
            return jsStats;
        }
        Path rootStats = directory.resolve(STATS_JSON);
        if (Files.exists(rootStats)) {
            return rootStats;
        }
        throw new ResultParsingException(
                "stats.json not found in " + directory.toAbsolutePath());
    }

    /**
     * Lit les statistiques globales depuis le fichier JSON.
     * Supporte deux formats Gatling 3.x : tableau "stats" ou map "contents".
     */
    JsonNode readGlobalStats(Path statsFile) {
        try {
            JsonNode root = objectMapper.readTree(statsFile.toFile());

            // Format 1 : tableau "stats" (Gatling 3.x standard)
            JsonNode statsArray = root.get("stats");
            if (statsArray != null && statsArray.isArray() && !statsArray.isEmpty()) {
                log.debug("action=read_stats format=array");
                return statsArray.get(0);
            }

            // Format 2 : map "contents" (Gatling 3.x alternatif)
            JsonNode contents = root.get("contents");
            if (contents != null && contents.isObject()) {
                log.debug("action=read_stats format=contents");
                // Chercher la cle "Global" ou "Global Information"
                JsonNode global = contents.get("Global");
                if (global != null) return global;
                global = contents.get("Global Information");
                if (global != null) return global;
                // Prendre le premier noeud
                var fields = contents.fields();
                if (fields.hasNext()) {
                    return fields.next().getValue();
                }
            }

            throw new ResultParsingException(
                    "Unrecognized stats.json format in " + statsFile);
        } catch (IOException e) {
            throw new ResultParsingException(
                    "Failed to read stats.json: " + e.getMessage(), e);
        }
    }

    // === JSON helpers ===

    private static JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) {
            throw new ResultParsingException(
                    "Missing required field: " + field);
        }
        return node;
    }

    private static long requireLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new ResultParsingException(
                    "Missing required numeric field: " + field);
        }
        return value.asLong();
    }

    private static double requireDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new ResultParsingException(
                    "Missing required numeric field: " + field);
        }
        return value.asDouble();
    }
}
