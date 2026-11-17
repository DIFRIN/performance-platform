package com.performance.platform.domain.assertion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogue ferme des metriques d'assertion.
 * <p>
 * Il porte deux responsabilites, et rien d'autre :
 * <ul>
 *   <li>identifier une metrique par un {@link MetricId} canonique ;</li>
 *   <li>normaliser les unites ({@code %}, {@code req/s}, {@code requests}).</li>
 * </ul>
 * Aucune dependance framework : utilisable par le domaine, le parser, les
 * adapters et le rapport.
 */
public final class MetricCatalog {

    private static final Map<String, MetricDefinition> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> UNITS = new LinkedHashMap<>();

    static {
        register("injection.latency.p50", MetricKind.QUANTILE, "ms", Reduction.MERGE, "Latence p50 (globale)");
        register("injection.latency.p50.worst-agent", MetricKind.QUANTILE, "ms", Reduction.MAX,
                "Latence p50 du pire agent");
        register("injection.latency.p75", MetricKind.QUANTILE, "ms", Reduction.MERGE, "Latence p75 (globale)");
        register("injection.latency.p75.worst-agent", MetricKind.QUANTILE, "ms", Reduction.MAX,
                "Latence p75 du pire agent");
        register("injection.latency.p90", MetricKind.QUANTILE, "ms", Reduction.MERGE, "Latence p90 (globale)");
        register("injection.latency.p90.worst-agent", MetricKind.QUANTILE, "ms", Reduction.MAX,
                "Latence p90 du pire agent");
        register("injection.latency.p95", MetricKind.QUANTILE, "ms", Reduction.MERGE, "Latence p95 (globale)");
        register("injection.latency.p95.worst-agent", MetricKind.QUANTILE, "ms", Reduction.MAX,
                "Latence p95 du pire agent");
        register("injection.latency.p99", MetricKind.QUANTILE, "ms", Reduction.MERGE, "Latence p99 (globale)");
        register("injection.latency.p99.worst-agent", MetricKind.QUANTILE, "ms", Reduction.MAX,
                "Latence p99 du pire agent");
        register("injection.latency.max", MetricKind.DURATION, "ms", Reduction.MAX, "Latence maximale");
        register("injection.latency.min", MetricKind.DURATION, "ms", Reduction.MIN, "Latence minimale");
        register("injection.latency.mean", MetricKind.DURATION, "ms", Reduction.MAX, "Latence moyenne");
        register("injection.requests.total", MetricKind.COUNTER, "count", Reduction.SUM,
                "Requetes envoyees");
        register("injection.requests.failed", MetricKind.COUNTER, "count", Reduction.SUM,
                "Requetes en echec");
        register("injection.error.rate", MetricKind.RATIO, "percent", Reduction.WEIGHTED,
                "Taux d'erreur");
        // Reduction documentee : somme(requetes) / fenetre commune mesuree ; calcul dans platform-assertion.
        register("injection.throughput", MetricKind.GAUGE, "req/s", Reduction.MIN,
                "Debit (fenetre commune)");
        register("injection.arrival.planned", MetricKind.COUNTER, "count", Reduction.SUM,
                "Arrivees planifiees");
        register("injection.arrival.achieved", MetricKind.COUNTER, "count", Reduction.SUM,
                "Arrivees realisees");
        register("injection.arrival.deviation", MetricKind.RATIO, "percent", Reduction.WEIGHTED,
                "Ecart arrivees realisees / planifiees");
        register("injection.start.skew", MetricKind.DURATION, "ms", Reduction.MAX,
                "Ecart de demarrage entre agents");
        register("injection.generator.cpu", MetricKind.RATIO, "percent", Reduction.MAX,
                "CPU du generateur");
        register("injection.generator.gc.pause", MetricKind.DURATION, "ms", Reduction.MAX,
                "Pause GC du generateur");
        register("database.scalar", MetricKind.GAUGE, "rows", Reduction.SUM,
                "Valeur scalaire SQL (COUNT, EXISTS...)");
        register("database.updates", MetricKind.COUNTER, "count", Reduction.SUM,
                "Lignes modifiees par une requete");
        register("kafka.messages.consumed", MetricKind.COUNTER, "count", Reduction.SUM,
                "Messages consommes");
        register("kafka.messages.produced", MetricKind.COUNTER, "count", Reduction.SUM,
                "Messages produits");
        register("kafka.consumer.lag", MetricKind.GAUGE, "messages", Reduction.MAX,
                "Lag consommateur");
        register("http.requests.matched", MetricKind.COUNTER, "count", Reduction.SUM,
                "Appels HTTP correspondants");
        register("http.requests.unmatched", MetricKind.COUNTER, "count", Reduction.SUM,
                "Appels HTTP non correspondants");
        register("filesystem.exists", MetricKind.STATE, "boolean", Reduction.ALL,
                "Presence d'un fichier");
        register("filesystem.size", MetricKind.GAUGE, "bytes", Reduction.MAX,
                "Taille d'un fichier");
        register("filesystem.checksum", MetricKind.STATE, "hex", Reduction.ALL,
                "Empreinte SHA-256 d'un fichier");
        register("runtime.cpu.utilization", MetricKind.RATIO, "percent", Reduction.MAX,
                "Utilisation CPU");
        register("runtime.memory.utilization", MetricKind.RATIO, "percent", Reduction.MAX,
                "Utilisation memoire");

        // Unites
        aliasUnit("ms", "ms");
        aliasUnit("millisecond", "ms");
        aliasUnit("milliseconds", "ms");
        aliasUnit("%", "percent");
        aliasUnit("pct", "percent");
        aliasUnit("percent", "percent");
        aliasUnit("req/s", "req/s");
        aliasUnit("rps", "req/s");
        aliasUnit("req_per_sec", "req/s");
        aliasUnit("requests/second", "req/s");
        aliasUnit("count", "count");
        aliasUnit("requests", "count");
        aliasUnit("rows", "rows");
        aliasUnit("messages", "messages");
        aliasUnit("bytes", "bytes");
        aliasUnit("boolean", "boolean");
        aliasUnit("hex", "hex");
    }

    private MetricCatalog() {
    }

    private static void register(String name, MetricKind kind, String unit,
                                 Reduction reduction, String description) {
        MetricId id = MetricId.of(name);
        BY_NAME.put(name, new MetricDefinition(id, kind, unit, reduction, description));
    }

    private static void aliasUnit(String alias, String canonical) {
        UNITS.put(alias.toLowerCase(Locale.ROOT), canonical);
    }

    /** @return toutes les definitions canoniques, dans l'ordre de declaration. */
    public static List<MetricDefinition> all() {
        return List.copyOf(BY_NAME.values());
    }

    /**
     * Resout une metrique par son nom canonique.
     *
     * @param name nom saisi dans un scenario
     * @return la definition, ou vide si inconnue
     */
    public static Optional<MetricDefinition> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * Resout une metrique ou leve une exception (usage : validation de scenario).
     *
     * @param name nom saisi
     * @return la definition
     * @throws IllegalArgumentException si le nom est inconnu
     */
    public static MetricDefinition require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown metric '" + name + "'. Known metrics: " + knownNames()));
    }

    /** @return la liste courte des noms canoniques, pour les messages d'erreur. */
    public static String knownNames() {
        return String.join(", ", BY_NAME.keySet());
    }

    /**
     * Normalise une unite ou echoue.
     *
     * @param unit unite saisie (peut etre null)
     * @return l'unite canonique, ou null si l'entree est nulle
     * @throws IllegalArgumentException si l'unite est inconnue
     */
    public static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String normalized = UNITS.get(unit.toLowerCase(Locale.ROOT));
        if (normalized == null) {
            throw new IllegalArgumentException("Unknown unit '" + unit
                    + "'. Known units: " + String.join(", ", UNITS.keySet()));
        }
        return normalized;
    }

    /**
     * Verifie la coherence unite/metrique.
     *
     * @param definition metrique canonique
     * @param unit       unite normalisee (peut etre null)
     * @return true si l'unite correspond a la definition
     */
    public static boolean unitMatches(MetricDefinition definition, String unit) {
        return unit == null || definition.unit().equals(unit);
    }
}
