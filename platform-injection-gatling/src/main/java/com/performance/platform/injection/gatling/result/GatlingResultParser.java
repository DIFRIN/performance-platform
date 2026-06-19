package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import java.nio.file.Path;

/**
 * Parse les resultats d'une simulation Gatling en {@link InjectionResult}.
 * <p>
 * Lit le fichier {@code stats.json} genere par Gatling et extrait les
 * metriques globales : compteurs de requetes, latences (p50-p99),
 * throughput, taux d'erreur.
 * <p>
 * Les implementations doivent etre thread-safe et sans etat mutable.
 */
@FunctionalInterface
public interface GatlingResultParser {

    /**
     * Parse le repertoire de resultats Gatling.
     *
     * @param gatlingResultDirectory le repertoire contenant {@code stats.json}
     * @param taskId                 l'identifiant de la tache associee
     * @return le resultat d'injection agrege
     * @throws ResultParsingException si le fichier est absent,
     *         inaccessible ou malforme
     */
    InjectionResult parse(Path gatlingResultDirectory, TaskId taskId)
            throws ResultParsingException;
}
