package com.performance.platform.plugin.resource;

import java.time.Duration;

/**
 * Port sortant d'ouverture d'un {@link MessageChannel} vers une destination d'une cible nommee.
 * <p>
 * Neutre vis-a-vis de la technologie : l'infrastructure fournit l'implementation correspondant
 * a la configuration Spring, les modules d'injection n'en dependent pas.
 */
public interface MessageChannelFactory {

    /**
     * @param target      nom de la cible configuree
     * @param destination nom logique de la destination, resolu en destination physique via la config de la cible
     * @param timeout     borne des operations d'envoi
     * @throws IllegalArgumentException si la cible est inconnue
     */
    MessageChannel open(String target, String destination, Duration timeout);

    /**
     * Description serialisable du canal que {@link #open} ouvrirait, pour le rouvrir dans un autre
     * processus (ADR-071).
     *
     * @throws IllegalArgumentException si la cible est inconnue
     * @throws UnsupportedOperationException si la technologie ne peut pas etre decrite
     */
    default ChannelDescriptor describe(String target, String destination) {
        throw new UnsupportedOperationException("Channel cannot be described for another process");
    }
}
