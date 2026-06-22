package com.performance.platform.transport.kafka;

import com.performance.platform.transport.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Souscription Kafka associee a un {@link KafkaExecutionTransport}.
 * <p>
 * Dans le nouveau design (ISSUE-090), l'annulation execute un
 * {@link Runnable} de nettoyage qui retire le handler du
 * {@link DynamicKafkaListenerRegistry} et stoppe le container
 * si c'est le dernier handler actif.
 * <p>
 * Package-private — utilisee par {@link KafkaExecutionTransport}.
 */
final class KafkaSubscription implements Subscription {

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Runnable cleanup;

    /**
     * @param cleanup action de nettoyage : retire le handler du registry
     *                et stoppe le container si dernier handler
     */
    KafkaSubscription(Runnable cleanup) {
        this.cleanup = cleanup;
    }

    @Override
    public void cancel() {
        if (active.compareAndSet(true, false)) {
            cleanup.run();
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
