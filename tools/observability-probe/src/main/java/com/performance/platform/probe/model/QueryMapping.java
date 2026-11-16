package com.performance.platform.probe.model;

/**
 * Mapping d'une reponse vers une metrique canonique.
 *
 * @param refId  identifiant de la requete dans le panel
 * @param metric nom canonique de la metrique
 * @param unit   unite
 */
public record QueryMapping(String refId, String metric, String unit) {

    /**
     * Analyse la forme {@code refId:metricId:unit}.
     *
     * @param value valeur CLI
     * @return le mapping
     */
    public static QueryMapping parse(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.startsWith("refId=")) {
            normalized = normalized.substring("refId=".length());
        }
        String[] parts = normalized.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid --map '" + value + "'. Expected refId:metric:unit");
        }
        return new QueryMapping(parts[0], parts[1], parts.length == 3 ? parts[2] : "");
    }
}
