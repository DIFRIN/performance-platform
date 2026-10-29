package com.performance.platform.transport;

/**
 * Represente une souscription active a des {@code ExecutionEvent} via
 * {@link ExecutionTransport#subscribe(ExecutionEventHandler)}.
 * <p>
 * La souscription peut etre annulee a tout moment via {@link #cancel()}.
 * Une fois annulee, {@link #isActive()} retourne {@code false} et le
 * handler ne recoit plus d'evenements.
 */
public interface Subscription {

    /**
     * Annule cette souscription. Apres appel, le handler enregistre
     * ne recoit plus d'evenements. Appels multiples sans effet.
     */
    void cancel();

    /**
     * Retourne {@code true} si cette souscription est toujours active
     * (non annulee).
     *
     * @return true si active, false si annulee
     */
    boolean isActive();
}
