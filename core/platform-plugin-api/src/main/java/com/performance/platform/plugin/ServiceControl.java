package com.performance.platform.plugin;

import java.time.Duration;

/**
 * Controle de duree de vie d'une service step (ADR-054).
 * <p>
 * Le service reste vivant jusqu'au STOP envoye par l'orchestrateur. Un agent
 * qui heberge une service step doit conserver son etat entre le demarrage et
 * le STOP ; la perte de l'agent fait echouer l'etape.
 *
 * <p>0 annotation framework, interface Java pure.</p>
 */
public interface ServiceControl {

    /**
     * Bloque jusqu'au STOP du service ou jusqu'au timeout.
     *
     * @return true si le STOP a ete demande, false si le delai est expire
     * @throws InterruptedException si le thread est interrompu
     */
    boolean awaitStop(Duration timeout) throws InterruptedException;

    /**
     * Bloque jusqu'au STOP du service.
     *
     * @throws InterruptedException si le thread est interrompu
     */
    void awaitStop() throws InterruptedException;

    /**
     * Indique si le STOP a deja ete demande.
     */
    boolean isStopRequested();
}
