package com.performance.platform.probe.model;

import java.time.Instant;

/** Empreinte de requete, suffisante pour rejouer la lecture. */
public record ProbeFingerprint(String provider, String source, String accessMode,
                               String panelRef, String dashboardVersion,
                               String resolvedQuery, String resolution,
                               String datasourceUid, String windowFrom, String windowTo,
                               Instant fetchedAt) {

    public String canonical() {
        return provider + "|" + source + "|" + accessMode + "|" + panelRef + "|"
                + dashboardVersion + "|" + datasourceUid + "|" + resolution + "|"
                + windowFrom + "|" + windowTo + "|" + resolvedQuery;
    }
}
