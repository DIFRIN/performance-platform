package com.performance.platform.engine.retry;

import com.performance.platform.domain.execution.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Implementation du RetryExecutor avec backoff exponentiel plafonne.
 * Utilise Thread.sleep (acceptable sous Virtual Threads).
 * <p>
 * Formule du delai pour l'attempt n (1-based) :
 * delay = min(initialDelay × multiplier^(n-1), maxDelay)
 * Apres un echec, le delai est calcule avant la prochaine tentative.
 */
@Component
public class DefaultRetryExecutor implements RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetryExecutor.class);

    @Override
    public <T> T executeWithRetry(RetryPolicy policy, Supplier<T> action) {
        Exception lastException = null;

        for (int attempt = 0; attempt < policy.maxAttempts(); attempt++) {
            try {
                T result = action.get();
                if (attempt > 0) {
                    log.info("action=retry_success attempt={}/{}", attempt + 1, policy.maxAttempts());
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("action=retry_failed attempt={}/{} error={}", attempt + 1, policy.maxAttempts(), e.getMessage());

                if (!isRetryable(policy.retryableExceptions(), e)) {
                    log.warn("action=retry_non_retryable exceptionClass={}", e.getClass().getName());
                    sneakyThrow(e);
                }

                if (attempt < policy.maxAttempts() - 1) {
                    long delayMs = computeDelayMs(policy, attempt + 1);
                    log.info("action=retry_sleep delayMs={}", delayMs);
                    sleep(delayMs);
                }
            }
        }

        log.error("action=retry_exhausted maxAttempts={} lastError={}", policy.maxAttempts(), lastException.getMessage());
        sneakyThrow(lastException);
        throw new AssertionError("unreachable");
    }

    /**
     * Verifie si l'exception est retryable selon la politique.
     * Si l'ensemble des exceptions retryables est vide, toutes les exceptions sont retryables.
     */
    private boolean isRetryable(Set<Class<? extends Exception>> retryableExceptions, Exception e) {
        if (retryableExceptions.isEmpty()) {
            return true;
        }
        return retryableExceptions.stream().anyMatch(cls -> cls.isInstance(e));
    }

    /**
     * Formule du backoff exponentiel plafonne.
     * delay = min(initialDelay × multiplier^(attemptNumber - 1), maxDelay)
     *
     * @param policy       la politique de retry
     * @param attemptNumber numero de la tentative (1-based) pour laquelle on calcule le delai d'attente
     * @return le delai en millisecondes
     */
    long computeDelayMs(RetryPolicy policy, int attemptNumber) {
        double exponent = attemptNumber - 1;
        double rawDelayMs = policy.initialDelay().toMillis() * Math.pow(policy.multiplier(), exponent);
        return Math.min((long) rawDelayMs, policy.maxDelay().toMillis());
    }

    /**
     * Sleep via Thread.sleep. Sous Virtual Threads, cette methode libere le carrier thread.
     */
    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", e);
        }
    }

    /**
     * Sneaky throw — permet de propager une exception verifiee sans la declarer.
     * Utilise car Supplier.get() ne declare pas de checked exception, mais le compilateur
     * ne peut pas le deduire du bloc catch (Exception).
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
