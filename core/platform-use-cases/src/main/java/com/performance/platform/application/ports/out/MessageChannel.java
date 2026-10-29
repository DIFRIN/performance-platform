package com.performance.platform.application.ports.out;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Canal de sortie vers une destination donnee. Asynchrone : le futur complete a l'acquittement. */
public interface MessageChannel extends AutoCloseable {

    CompletableFuture<Void> publish(String key, String value, Map<String, String> headers);

    @Override
    void close();
}