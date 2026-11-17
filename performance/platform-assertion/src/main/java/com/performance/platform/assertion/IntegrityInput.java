package com.performance.platform.assertion;

/**
 * Metriques agregees d'une etape d'injection necessaires aux controles d'integrite (ADR-069).
 *
 * @param arrivalDeviationPercent ecart d'arrivee observe en pourcent
 * @param arrivalKnown            faux pour une simulation opaque (ecart non calculable)
 * @param grantedAgents           agents octroyes a l'etape
 * @param reportingAgents         agents ayant retourne un resultat
 * @param complete                faux si le worker a ete arrete avant la fin du plan
 * @param generatorCpuPercent     CPU maximal du generateur en pourcent
 */
public record IntegrityInput(double arrivalDeviationPercent, boolean arrivalKnown, int grantedAgents,
                             int reportingAgents, boolean complete, double generatorCpuPercent) {
}
