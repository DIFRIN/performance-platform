package com.performance.platform.plugin.resource;

import java.time.Duration;

/**
 * SPI (ServiceLoader) rouvrant un canal a partir de son {@link ChannelDescriptor}, sans contexte
 * Spring. Implemente par le module de capacite proprietaire de la technologie (ADR-057, ADR-071).
 */
public interface ChannelOpener {

    /** Valeur de {@link ChannelDescriptor#type()} prise en charge. */
    String type();

    MessageChannel open(ChannelDescriptor descriptor, Duration timeout);
}
