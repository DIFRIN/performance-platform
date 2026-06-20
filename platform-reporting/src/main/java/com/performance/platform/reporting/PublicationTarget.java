package com.performance.platform.reporting;

/**
 * Destinations de publication externe pour un rapport de campagne.
 * Défini dans platform-reporting (pas platform-domain) car ces valeurs
 * représentent des outils/plateformes externes — concept infrastructure,
 * pas concept métier.
 */
public enum PublicationTarget {
    CONFLUENCE,
    S3,
    SHAREPOINT,
    GIT,
    NEXUS,
    CUSTOM
}
