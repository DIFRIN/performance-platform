package com.performance.platform.application.ports.out;

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
}