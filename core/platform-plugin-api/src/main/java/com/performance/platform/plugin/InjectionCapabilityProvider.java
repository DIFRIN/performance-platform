package com.performance.platform.plugin;

/**
 * Interface optionnelle implementee par un executor d'injection pour declarer ses capacites.
 * Ne modifie pas {@link TaskExecutor}. Reference : ADR-065.
 */
public interface InjectionCapabilityProvider {

    InjectionCapabilities injectionCapabilities();
}
