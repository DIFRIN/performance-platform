package com.performance.platform.transport.rabbitmq;

import com.performance.platform.transport.Subscription;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Souscription RabbitMQ associee a un {@link RabbitMQExecutionTransport}.
 * L'annulation retire le handler du registre des souscriptions.
 * <p>
 * Package-private — utilisee par {@link RabbitMQConsumerManager} pour
 * verifier l'etat des souscriptions actives.
 */
final class RabbitMQSubscription implements Subscription {

    private final ConcurrentMap<RabbitMQSubscription, ?> registry;
    private final AtomicBoolean active = new AtomicBoolean(true);

    RabbitMQSubscription(ConcurrentMap<RabbitMQSubscription, ?> registry) {
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
