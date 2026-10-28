package com.performance.platform.application.ports.out;

/** Resolves a configured resource by logical name without exposing its storage mechanism. */
public interface NamedResourceResolver<T> {
    /** Returns the named resource, or null when the name is unknown. */
    T resolve(String name);
}
