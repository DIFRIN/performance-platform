package com.performance.platform.application.ports.out;

/**
 * Port sortant de resolution des cibles HTTP nommees.
 * <p>
 * Permet aux modules fournisseurs (assertions) de resoudre une cible sans
 * dependre de {@code platform-infrastructure}. L'implementation par defaut
 * est {@code HttpTargetRegistry}.
 */
public interface HttpTargetResolver {

    /**
     * Resout une cible HTTP par son nom logique.
     *
     * @param targetName nom de la cible (cle de {@code platform.http-targets})
     * @return la cible, ou {@code null} si inconnue
     */
    HttpTarget resolve(String targetName);
}
