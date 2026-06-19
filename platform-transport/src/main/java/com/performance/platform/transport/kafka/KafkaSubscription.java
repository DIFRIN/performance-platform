package com.performance.platform.transport.kafka;

import com.performance.platform.transport.Subscription;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Souscription Kafka associee a un {@link KafkaExecutionTransport}.
 * L'annulation retire le handler du registre des souscriptions.
 * <p>
 * Package-private — utilisee par {@link KafkaConsumerManager} pour
 * verifier l'etat des souscriptions actives.
 */
final class KafkaSubscription implements Subscription {

    private final ConcurrentMap<KafkaSubscription, ?> registry;
    private final AtomicBoolean active = new AtomicBoolean(true);

    KafkaSubscription(ConcurrentMap<KafkaSubscription, ?> registry) {
        this.registry = registry;
    }

    @Override
    public void cancel() {
        if (active.compareAndSet(true, false)) {
            registry.remove(this);
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
