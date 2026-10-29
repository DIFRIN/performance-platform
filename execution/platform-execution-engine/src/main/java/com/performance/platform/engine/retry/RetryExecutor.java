package com.performance.platform.engine.retry;

import com.performance.platform.domain.execution.RetryPolicy;

import java.util.function.Supplier;

/**
 * Strategy permettant d'executer une action avec une politique de retry.
 * Abstrait la logique de backoff exponentiel plafonne.
 */
public interface RetryExecutor {

    /**
     * Execute l'action en applicant la politique de retry.
     * Si l'action echoue avec une exception retryable, elle est rejouee
     * apres un delai de backoff exponentiel (plafonne a maxDelay).
     * Si maxAttempts est atteint, la derniere exception est propagee.
     *
     * @param policy la politique de retry (maxAttempts, backoff, exceptions filtrables)
     * @param action l'action a executer
     * @param <T>    le type de retour de l'action
     * @return le resultat de l'action si elle reussit dans maxAttempts tentatives
     * @throws RuntimeException si l'action echoue definitivement
     */
    <T> T executeWithRetry(RetryPolicy policy, Supplier<T> action);
}
