package com.performance.platform.probe.model;

import java.util.List;

/** Resultat d'une lecture de panel (sortie JSON du CLI). */
public record ProbeResult(String source, String accessMode, ProbeWindowDto window,
                          ProbeFingerprint fingerprint, List<ProbeSeries> observations) {

    public ProbeResult {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    /** Forme serialisee de la fenetre. */
    public record ProbeWindowDto(String from, String to, String resolution) {
    }
}
