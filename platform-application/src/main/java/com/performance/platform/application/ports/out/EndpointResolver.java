package com.performance.platform.application.ports.out;

/**
 * Port sortant de resolution des cibles nommees.
 * <p>
 * Permet aux modules fournisseurs (assertions) de resoudre une cible sans
 * dependre de {@code platform-infrastructure}. L'implementation est portee par l'infrastructure.
 * 
 */
public interface EndpointResolver {

    /**
     * Resout une cible par son nom logique.
     *
     * @param targetName nom de la cible
     * @return la cible, ou {@code null} si inconnue
     */
    Endpoint resolve(String targetName);
}
