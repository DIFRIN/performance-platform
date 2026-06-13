package com.performance.platform.domain.id;

import java.util.UUID;

/**
 * Identifiant unique d'un rapport de campagne.
 * Value object immuable, 0-annotation framework.
 */
public record ReportId(String value) {

    public static ReportId generate() {
        return new ReportId(UUID.randomUUID().toString());
    }
}
