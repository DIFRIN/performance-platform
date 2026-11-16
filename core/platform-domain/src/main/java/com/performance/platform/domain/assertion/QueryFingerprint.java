package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Objects;

/**
 * Empreinte d'une requete externe, suffisante pour rejouer une observation.
 * <p>
 * Une empreinte differente entre deux executions d'un meme scenario signale
 * soit une modification du dashboard, soit un fallback de canal d'acces.
 *
 * @param provider         fournisseur logique ({@code grafana}, {@code kibana})
 * @param source           nom logique de la source ({@code grafana-prod})
 * @param accessMode       canal utilise ({@code GRAFANA_DS_QUERY},
 *                         {@code ES_DIRECT}, {@code CONSOLE_PROXY})
 * @param panelRef         dashboard/visualisation + panel
 * @param dashboardVersion version lue, si disponible
 * @param resolvedQuery    requete finale (variables substituees)
 * @param resolution       pas d'echantillonnage effectif
 * @param datasourceUid    datasource interrogee, si applicable
 * @param window           fenetre demandee
 * @param fetchedAt        instant de lecture
 */
public record QueryFingerprint(
        String provider,
        String source,
        String accessMode,
        String panelRef,
        String dashboardVersion,
        String resolvedQuery,
        String resolution,
        String datasourceUid,
        TimeWindow window,
        Instant fetchedAt) {

    public QueryFingerprint {
        Objects.requireNonNull(provider, "provider required");
        Objects.requireNonNull(source, "source required");
        panelRef = panelRef == null ? "" : panelRef;
        dashboardVersion = dashboardVersion == null ? "" : dashboardVersion;
        resolvedQuery = resolvedQuery == null ? "" : resolvedQuery;
        resolution = resolution == null ? "" : resolution;
        datasourceUid = datasourceUid == null ? "" : datasourceUid;
        Objects.requireNonNull(window, "window required");
        Objects.requireNonNull(fetchedAt, "fetchedAt required");
    }

    /** Empreinte neutre pour une mesure interne (SQL, fichier, output de tache). */
    public static QueryFingerprint internal(String source, TimeWindow window) {
        return new QueryFingerprint("internal", source, "INTERNAL", "", "", "", "",
                "", window, Instant.now());
    }

    /** @return une forme stable et lisible, utilisee comme cle d'audit. */
    public String canonical() {
        return provider + "|" + source + "|" + accessMode + "|" + panelRef + "|"
                + dashboardVersion + "|" + datasourceUid + "|" + resolution + "|"
                + window.from() + "|" + window.to() + "|" + resolvedQuery;
    }
}
